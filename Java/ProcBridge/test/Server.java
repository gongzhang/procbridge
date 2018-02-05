import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import co.gongzh.procbridge.APIHandler;
import co.gongzh.procbridge.ProcBridgeServer;

/**
 * @author Gong Zhang
 */
public class Server {

    public static void main(String[] args) {

        int port = 8877;

        ProcBridgeServer server = new ProcBridgeServer(port);
        server.setDelegate(new Object() {

            @APIHandler
            JsonObject echo(JsonObject arg) {
            	System.out.println("Received echo : " + arg);
                return arg;
            }

            @APIHandler
            JsonObject retNull(JsonObject arg) {
            	System.out.println("Received retNull : " + arg);
                return null;
            }
            
            @APIHandler
            JsonObject retNullVal(JsonObject arg) {
            	System.out.println("Received retNullVal : " + arg);
                JsonObject res =  new JsonObject();
                res.add("result", null);
                return res;
            }
            
            @APIHandler 
            JsonObject add(JsonObject arg) {
            	System.out.println("Received add : " + arg);
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
            server.start(true);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("exit")) {
                        break;
                    } else {
                    	JsonObject message = new JsonObject();
                    	message.addProperty("body", line);
                    	try {
							server.sendMessage("message", message);
						} catch (Exception e) {
							e.printStackTrace();
						}
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
