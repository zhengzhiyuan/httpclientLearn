package httpclientLearn.async;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.http.Consts;
import org.apache.http.client.CookieStore;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncClientPool {
    private enum SingletonPool {
        Pool;
        private final PoolingNHttpClientConnectionManager cm;
        private final ScheduledExecutorService schedluedES = Executors.newScheduledThreadPool(1);
        private final Logger logger = LoggerFactory.getLogger(getClass());

        private SingletonPool() {
            try {
                this.cm = init();
                // 定时把过期链接清除
                IdleConnectionMonitorThread monitor = new IdleConnectionMonitorThread(cm);
                schedluedES.scheduleAtFixedRate(monitor, 0, 5, TimeUnit.SECONDS);
                logger.info("{} init success", this);
            } catch (IOReactorException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * 初始化连接配置
         *
         * @return
         * @throws IOReactorException
         */
        private PoolingNHttpClientConnectionManager init() throws IOReactorException {
            // 配置io线程
            IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(Runtime.getRuntime().availableProcessors()).build();
            ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

            // 设置协议http和https对应的处理socket链接工厂的对象
            SSLContext sslcontext = SSLContexts.createDefault();
            Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy> create().register("http", NoopIOSessionStrategy.INSTANCE)
                    .register("https", new SSLIOSessionStrategy(sslcontext)).build();

            // Create a connection manager with custom configuration.
            PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor, null, sessionStrategyRegistry, null);

            // Configure total max or per route limits for persistent connections
            // that can be kept in the pool or leased by the connection manager.
            cm.setMaxTotal(200);
            cm.setDefaultMaxPerRoute(20);

            // Create message constraints
            final MessageConstraints messageConstraints = MessageConstraints.custom().setMaxHeaderCount(200).setMaxLineLength(2000).build();

            // Create connection configuration
            final ConnectionConfig connectionConfig = ConnectionConfig.custom().setMalformedInputAction(CodingErrorAction.IGNORE).setUnmappableInputAction(CodingErrorAction.IGNORE)
                    .setCharset(Consts.UTF_8).setMessageConstraints(messageConstraints).build();
            // Configure the connection manager to use connection configuration either
            // by default or for a specific host.
            cm.setDefaultConnectionConfig(connectionConfig);
            return cm;
        }

        public CloseableHttpAsyncClient getHttpClient() {
            // Use custom cookie store if necessary.
            final CookieStore cookieStore = new BasicCookieStore();
            return HttpAsyncClients.custom().setConnectionManager(cm).setDefaultCookieStore(cookieStore).build();
        }

        public void shutdown() throws IOException {
            if (!schedluedES.isShutdown()) {
                schedluedES.shutdownNow();
            }
            cm.shutdown();
            logger.info("{} shutdown success", this);
        }
    }

    private static class IdleConnectionMonitorThread implements Runnable {

        private final Logger logger = LoggerFactory.getLogger(getClass());
        private final PoolingNHttpClientConnectionManager cm;

        public IdleConnectionMonitorThread(PoolingNHttpClientConnectionManager cm) {
            this.cm = cm;
        }

        @Override
        public void run() {
            // Close expired connections
            cm.closeExpiredConnections();
            // Optionally, close connections
            // that have been idle longer than 30 sec
            cm.closeIdleConnections(30, TimeUnit.SECONDS);
            logger.trace("Status: {}", cm.getTotalStats());
        }
    }

    public static CloseableHttpAsyncClient getClient() {
        return SingletonPool.Pool.getHttpClient();
    }

    public static void shutdown() throws IOException {
        SingletonPool.Pool.shutdown();
    }

}
