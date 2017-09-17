import co.gongzh.procbridge.APIHandler;
import co.gongzh.procbridge.ProcBridgeServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * @author Gong Zhang
 */
public class Server {

    public static void main(String[] args) {

        int port = 8877;

        ProcBridgeServer server = new ProcBridgeServer(port, new Object() {

            @APIHandler JSONObject echo(JSONObject arg) {
                return arg;
            }

            @APIHandler JSONObject add(JSONObject arg) {
                JSONArray elements = arg.getJSONArray("elements");
                int sum = 0;
                for (int i = 0; i < elements.length(); i++) {
                    sum += elements.getInt(i);
                }
                JSONObject result = new JSONObject();
                result.put("result", sum);
                return result;
            }

        });

        try {
            server.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("exit")) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            }

            server.stop();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
