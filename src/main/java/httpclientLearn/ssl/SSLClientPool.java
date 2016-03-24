package httpclientLearn.ssl;

import java.nio.charset.CodingErrorAction;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.http.Consts;
import org.apache.http.client.CookieStore;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SSLClientPool {

    private enum SingletonPool {
        SSLPool;
        private final PoolingHttpClientConnectionManager cm;
        private final ScheduledExecutorService schedluedES = Executors.newScheduledThreadPool(1);
        private final Logger logger = LoggerFactory.getLogger(getClass());

        private final SSLConnectionSocketFactory sslsf;

        private SingletonPool() {
            try {
                // 1 连接配置初始化
                this.cm = init();

                // 2 SSL配置初始化
                this.sslsf = initSSL();

                // 3 定时把过期链接清除
                IdleConnectionMonitorThread monitor = new IdleConnectionMonitorThread(cm);
                schedluedES.scheduleAtFixedRate(monitor, 0, 5, TimeUnit.SECONDS);
                logger.info("{} init success", this);
            } catch (Exception e) {
                logger.error(this + " init exception", e);
                throw new RuntimeException("", e);
            }
        }

        /**
         * 连接配置初始化
         *
         * @return
         */
        private PoolingHttpClientConnectionManager init() {
            // Create a connection manager with custom configuration.
            final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();

            // Configure total max or per route limits for persistent connections
            // that can be kept in the pool or leased by the connection manager.
            cm.setMaxTotal(200);
            cm.setDefaultMaxPerRoute(20);

            // Create socket configuration
            final SocketConfig socketConfig = SocketConfig.custom().setTcpNoDelay(true).build();

            // Configure the connection manager to use socket configuration either
            // by default or for a specific host.
            cm.setDefaultSocketConfig(socketConfig);

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

        /**
         * 初始化SSL参数
         *
         * @return
         * @throws KeyManagementException
         * @throws NoSuchAlgorithmException
         * @throws KeyStoreException
         */
        public SSLConnectionSocketFactory initSSL() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
            // 1 创建一个SSLContext——设置证书密码
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {

                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // TODO Auto-generated method stub
                    return true;
                }

            }).build();
            // 2 创建工厂SSLConnectionSocketFactory——设置协议
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);

            return sslsf;
        }

        public CloseableHttpClient getHttpClient() {
            // Use custom cookie store if necessary.
            final CookieStore cookieStore = new BasicCookieStore();
            return HttpClients.custom().setConnectionManager(cm).setSSLSocketFactory(sslsf).setDefaultCookieStore(cookieStore).build();
        }

        public void shutdown() {
            if (!schedluedES.isShutdown()) {
                schedluedES.shutdownNow();
            }
            cm.shutdown();
            logger.info("{} shutdown success", this);
        }
    }

    private static class IdleConnectionMonitorThread implements Runnable {

        private final Logger logger = LoggerFactory.getLogger(getClass());
        private final PoolingHttpClientConnectionManager cm;

        public IdleConnectionMonitorThread(PoolingHttpClientConnectionManager cm) {
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

    public static CloseableHttpClient getClient() {
        return SingletonPool.SSLPool.getHttpClient();
    }

    public static void shutdown() {
        SingletonPool.SSLPool.shutdown();
    }

}
