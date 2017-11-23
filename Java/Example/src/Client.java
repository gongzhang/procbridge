import co.gongzh.procbridge.ProcBridge;
import co.gongzh.procbridge.ProcBridgeException;
import com.google.gson.JsonObject;

/**
 * @author Gong Zhang
 */
public class Client {

    public static void main(String[] args) {

        String host = "127.0.0.1";
        int port = 8877;
        int timeout = 10000; // 10 seconds

        ProcBridge pb = new ProcBridge(host, port, timeout);

        try {
            JsonObject resp;

            resp = pb.request("echo", "{}");
            System.out.println(resp);

            resp = pb.request("add", "{elements: [1, 2, 3, 4, 5]}");
            System.out.println(resp);

        } catch (ProcBridgeException e) {
            e.printStackTrace();
        }
    }

}
