import co.gongzh.procbridge.APIHandler;
import co.gongzh.procbridge.ProcBridgeServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

            @APIHandler
            JsonObject echo(JsonObject arg) {
                return arg;
            }

            @APIHandler JsonObject add(JsonObject arg) {
                JsonArray elements = arg.get("elements").getAsJsonArray();
                int sum = 0;
                for (int i = 0; i < elements.size(); i++) {
                    sum += elements.get(i).getAsInt();
                }
                JsonObject result = new JsonObject();
                result.addProperty("result", sum);
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
