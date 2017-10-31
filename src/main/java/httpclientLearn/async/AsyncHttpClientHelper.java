package httpclientLearn.async;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import config.Constants;
import httpclientLearn.dto.HttpResult;

public class AsyncHttpClientHelper {

    private static final AsyncHttpClientHelper instance = new AsyncHttpClientHelper();

    public static AsyncHttpClientHelper getInstance() {
        return instance;
    }

    private AsyncHttpClientHelper() {
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(Constants.SOCKET_TIME_OUT).setConnectTimeout(Constants.SOCKET_TIME_OUT)
            .setConnectionRequestTimeout(Constants.SOCKET_TIME_OUT).build();

    public void asyncGet(String uri, Map<String, String> headers, Map<String, Object> parameters) throws InterruptedException, ExecutionException {

        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("uri is required");
        }

        RequestBuilder requestBuilder = RequestBuilder.get();
        requestBuilder.setUri(uri);

        // Populate request parameters
        if (parameters != null && !parameters.isEmpty()) {
            for (final String key : parameters.keySet()) {
                if (parameters.get(key) != null) {
                    requestBuilder.addParameter(key, String.valueOf(parameters.get(key)));
                }
            }
        }

        // Request configuration can be overridden at the request level.
        // They will take precedence over the one set at the client level.
        requestBuilder.setConfig(defaultRequestConfig);

        // Set custom header
        if (headers != null && !headers.isEmpty()) {
            for (final String key : headers.keySet()) {
                requestBuilder.addHeader(key, headers.get(key));
            }
        }

        asyncParseRequest(AsyncClientPool.getClient(), requestBuilder.build());
    }

    private void asyncParseRequest(CloseableHttpAsyncClient httpClient, HttpUriRequest request) throws InterruptedException, ExecutionException {
        logger.debug("Executing request " + request.getURI());

        httpClient.start();
        httpClient.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
                try {
                    ProtocolVersion protocolVersion = response.getProtocolVersion();
                    int code = response.getStatusLine().getStatusCode();
                    String reasonPhrase = response.getStatusLine().getReasonPhrase();
                    HttpResult result = new HttpResult(protocolVersion, code, reasonPhrase);
                    populate(response, result);
                    logger.info(result.toString());
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }

            @Override
            public void failed(Exception e) {
                logger.error(e.getMessage(), e);
            }

            @Override
            public void cancelled() {
                logger.error("cancelled");
            }
        });

    }

    private HttpResult parseRequest(CloseableHttpAsyncClient httpClient, HttpUriRequest request) throws InterruptedException, ExecutionException {
        logger.debug("Executing request " + request.getURI());

        try {
            httpClient.start();
            final Future<HttpResponse> future = httpClient.execute(request, null);

            HttpResponse response = future.get();
            ProtocolVersion protocolVersion = response.getProtocolVersion();
            int code = response.getStatusLine().getStatusCode();
            String reasonPhrase = response.getStatusLine().getReasonPhrase();

            final HttpResult result = new HttpResult(protocolVersion, code, reasonPhrase);
            logger.trace("----------------------------------------");
            logger.trace("{}", response.getStatusLine());
            populate(response, result); // 将报文内容加入到result中
            logger.trace("----------------------------------------");

            return result;
        } catch (final ClientProtocolException ex) {
            logger.error("Unexpected Protocol error occurs while executing request {}", request.getURI(), ex);
        } catch (final IOException ex) {
            logger.error("Unexpected I/O error occurs while executing request {}", request.getURI(), ex);
        }

        return null;
    }

    private void populate(final HttpResponse response, final HttpResult result) throws IOException {

        Header[] headers = response.getAllHeaders();
        if (headers != null && headers.length > 0) {
            for (Header header : headers) {
                result.addHeader(header.getName(), header.getValue());
            }
        }

        final HttpEntity entity = response.getEntity();
        if (entity != null) {

            logger.trace("Response Content-Length: {}", entity.getContentLength());
            final ContentType contentType = ContentType.get(entity);

            if (contentType != null) {
                result.setContentType(contentType.getMimeType());
                Charset charset = contentType.getCharset();
                if (charset == null) {
                    charset = HTTP.DEF_CONTENT_CHARSET;
                }
                result.setCharset(charset);
            }
            result.setContentLength(entity.getContentLength());
            result.setResponseBody(EntityUtils.toByteArray(entity));
            EntityUtils.consume(entity);// 保证内容完全被消费掉，如果流存在则会被close
        }
    }
}
