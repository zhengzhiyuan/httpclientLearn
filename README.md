# httpclientLearn
learn httpclient demo

主要有两个Httpclient工具类

1 HttpClientPool：获取Httpclient的工具类，使用了线程池定期将过期或者空闲超时的连接删除，保证系统资源不浪费

2 HttpClientHelper:发送http请求的工具类。

===================================================================

httpclientLearn.ssl包：自定义ssl配置，例如加载证书之类
