package httpclientLearn.async;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

public class AsyncHttpClientHelperTest {

    @Test
    public void test() throws InterruptedException, ExecutionException, IOException {

        AsyncHttpClientHelper helper = AsyncHttpClientHelper.getInstance();
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("wd", "test");

        helper.asyncGet("http://baidu.com", null, null);
        Thread.sleep(50000);
//        AsyncClientPool.shutdown();
    }

}
