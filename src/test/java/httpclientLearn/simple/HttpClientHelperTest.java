package httpclientLearn.simple;

import httpclientLearn.dto.HttpResult;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class HttpClientHelperTest {

    @Test
    public void test() {

        HttpClientHelper helper = HttpClientHelper.getInstance();
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("wd", "test");

        HttpResult getResulet = helper.get("http://baidu.com");
        System.out.println("==================================");
        System.out.println(getResulet);

        HttpResult postResulet = helper.post("http://baidu.com/s", param);
        System.out.println("==================================");
        System.out.println(postResulet);

        System.out.println("==================================");
        HttpClientPool.shutdown();
    }
}
