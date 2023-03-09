import basic.annotation.Endpoint;
import basic.service.Service;

public class TestService extends Service {
    TestServiceProxy proxy;
    @Endpoint
    public String sendChatMsg(String msg) {
        return "hello";
    }
}
