package co.gongzh.procbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * @author Gong Zhang
 */
public final class ProcBridge {

	private static final JsonParser parser = new JsonParser();
	private final String host;
	private final int port;
	private int timeout;

	private Socket socket;
	
	private InputStream is;
	private Object isLock = new Object();
	
	private OutputStream os;
	private Object osLock = new Object();

	private MessageHandler messageHandler;
	private boolean stopRequested = false;

	public ProcBridge(String host, int port, int timeout, MessageHandler messageHandler) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;

		this.messageHandler = messageHandler;

		this.socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(host, port), timeout);

			// socket.setSoTimeout(timeout);

			is = socket.getInputStream();
			os = socket.getOutputStream();

		} catch (IOException e) {
			throw new RuntimeException("Socket can not be connected.", e);
		}

		startHandlingMessages();

	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public long getTimeout() {
		return timeout;
	}
	
	public int getClientID() throws ProcBridgeException {
		//TODO : implement it correctly !
		sendMessage(Protocol.GET_CLIENT_ID_API);
		return 0;
	}

	public void sendMessage(@NotNull String api) throws ProcBridgeException {
		sendMessage(api, (JsonObject) null);
	}

	public void sendMessage(@NotNull String api, @Nullable String jsonText) throws ProcBridgeException {
		try {
			if (jsonText == null) {
				jsonText = "{}";
			}
			JsonObject obj = parser.parse(jsonText).getAsJsonObject();
			sendMessage(api, obj);
		} catch (JsonParseException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public void sendMessage(@NotNull String api, @Nullable JsonObject body) throws ProcBridgeException {
		
		if (socket.isOutputShutdown()) {
			this.stopRequested = true;
			throw new ProcBridgeException("OutputStream closed from the server. No more messages can be handled. Please reconnect.");
		}
		
		synchronized (osLock) {
			final RequestEncoder request = new RequestEncoder(api, body);
			//just send the message here.
			Protocol.write(os, request);
		}
		
	}

	private void startHandlingMessages() {
		Thread threadMessageHandler = new Thread(() -> {
			while (!stopRequested) {
				try {
					
					if (socket.isInputShutdown()) {
						this.stopRequested = true;
						throw new ProcBridgeException("InputStream closed from the server. No more messages can be handled. Please reconnect.");
					}

					final JsonObject[] out_json = { null };
					final String[] out_err_msg = { null };
					
					synchronized (isLock) {
						Decoder decoder = Protocol.read(is);
						out_json[0] = decoder.getResponseBody();
						if (out_json[0] == null) {
							// must be error
							out_err_msg[0] = decoder.getErrorMessage();
						}
					}

					if (out_json[0] == null) {
						if (out_err_msg[0] == null) {
							out_err_msg[0] = "server error";
						}
						throw new ProcBridgeException(out_err_msg[0]);
					}

					assert out_json[0] != null;
					//sends the message to the handler.
					messageHandler.onMessage(out_json[0]);
					
				} catch (ProcBridgeException e) {
					if (stopRequested && socket.isClosed()) {
						//the current read operation generates a socket closed exception during a normal stop.
						//it is detected here not to send an error to the caller.
						return; //stop handler
					}
					messageHandler.onError(e);
				} catch (Throwable t) {
					//Prevent thread to die in case of any error.
					messageHandler.onError(new ProcBridgeException(t));
				}
			}
		});
		threadMessageHandler.setDaemon(true);
		threadMessageHandler.setName("pb_clientMessageReceiver");
		threadMessageHandler.start();
	}

	/**
	 * Stop must be called at the end of the ProcBridge usage.
	 */
	public void stop() {
		//sends a close message to server in order to close the socket for the current client.
		try {
			sendMessage(Protocol.CLOSE_MESSAGE_API);
		} catch (ProcBridgeException e1) {
			e1.printStackTrace();
		}
		stopRequested = true;
		if (!this.socket.isClosed()) {
			try {
				this.socket.close();
			} catch (IOException e) {
				throw new RuntimeException("Error closing socket : ", e);
			}
		}
		//Let the connection close normally before exiting.
		//This method is generally called just before exit of the client program and without this small time, 
		//The connection is closed by the end of the JVM and there is an error on the server side.
        try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
