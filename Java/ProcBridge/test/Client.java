import co.gongzh.procbridge.MessageHandler;
import co.gongzh.procbridge.ProcBridge;
import co.gongzh.procbridge.ProcBridgeException;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

/**
 * @author Gong Zhang
 */
public class Client {

	private static MessageHandler messageHandlerImpl = new MessageHandler() {
		
		@Override
		public void onMessage(@NotNull JsonObject message) {
			System.out.println("onMessage " + message);
			
		}
		
		@Override
		public void onError(ProcBridgeException e) {
			System.out.println("onError " + e);
			e.printStackTrace();
			
		}
	};
	
    public static void main(String[] args) {

        String host = "127.0.0.1";
        int port = 8877;
        int timeout = 10000; // 10 seconds

        ProcBridge pb = new ProcBridge(host, port, timeout, messageHandlerImpl);

        try {
        	
        	pb.getClientID();
        	
        	pb.sendMessage("echo", "{echo:echoooo}");

            pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}");

            try {
            	pb.sendMessage("retNull", "{}");

            } catch (RuntimeException e) {
            	e.printStackTrace();
            }
            
            pb.sendMessage("retNullVal", "{}");

            
        } catch (ProcBridgeException e) {
            e.printStackTrace();
        }
        
        //Wait for responses.
        try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        
        pb.stop();
        
        
    }

}
