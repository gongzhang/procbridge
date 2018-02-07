package co.gongzh.procbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * @author Gong Zhang
 */
public final class ProcBridgeServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcBridgeServer.class);

	interface Delegate {

		void onMessage(@NotNull String api, @NotNull JsonObject body) throws Exception;

		void onError(Exception e);

	}

	private final int port;
	private Delegate delegate;
	private boolean started;

	private ExecutorService executor;
	private ServerSocket serverSocket;

	private AtomicInteger clientIDSequence = new AtomicInteger(1);

	/**
	 * The key in the map is the clientID !
	 */
	private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<JsonObject>> mapOfMessagesToSendQueue = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, ConnectionSendMessages> mapOfClientSenders = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, Semaphore> mapOfClientSemaphores = new ConcurrentHashMap<>();

	public ProcBridgeServer(int port) {
		this.started = false;
		this.port = port;

		this.executor = null;
		this.serverSocket = null;
	}

	public synchronized boolean isStarted() {
		return started;
	}

	public int getPort() {
		return port;
	}

	public synchronized void setDelegate(@NotNull Object delegate) {
		this.delegate = new ReflectiveDelegate(this, delegate);
	}

	public synchronized void start() throws IOException {
		start(false);
	}

	public synchronized void start(boolean daemonThreads) throws IOException {
		if (started) {
			throw new IllegalStateException("Server already started.");
		}

		if (this.delegate == null) {
			throw new IllegalStateException("A delegate must be set before starting the server.");
		}

		this.serverSocket = new ServerSocket(this.port); // possible throw exception!

		if (daemonThreads) {
			this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = Executors.defaultThreadFactory().newThread(r);
					t.setDaemon(true);
					return t;
				}
			});
		} else {
			this.executor = Executors.newCachedThreadPool();
		}

		executor.execute(() -> {
			Thread.currentThread().setName("pb_ServerSocketListener_" + getPort());
			while (true) {
				try {
					Socket socket = serverSocket.accept();
					int clientID = clientIDSequence.getAndIncrement();
					ConnectionReceiveMessages receiver = new ConnectionReceiveMessages(socket, clientID, this);
					Semaphore semaphore = new Semaphore(1);
					this.mapOfClientSemaphores.put(clientID, semaphore);
					this.mapOfMessagesToSendQueue.put(clientID, new ConcurrentLinkedQueue<>());
					this.mapOfClientSenders.put(clientID, new ConnectionSendMessages(socket, clientID, this));
					synchronized (ProcBridgeServer.this) {
						if (!started) {
							return; // finish listener
						}
						executor.execute(receiver);
						executor.execute(this.mapOfClientSenders.get(clientID));
					}
				} catch (IOException ignored) {
					return; // finish listener
				}
			}
		});

		started = true;
	}

	public synchronized void stop() {
		if (!started) {
			throw new IllegalStateException("Server did not started.");
		}

		executor.shutdown();
		executor = null;

		try {
			serverSocket.close();
		} catch (IOException ignored) {
		}
		serverSocket = null;

		this.started = false;
	}

	public void sendMessage(int clientID, @NotNull JsonObject response) throws Exception {

		ConnectionSendMessages sender = mapOfClientSenders.get(clientID);

		if (sender == null) {
			throw new RuntimeException(
			        "Please establish a connection from a client ( " + clientID + ") before sending messages !");
		}

		// put it on the queue to be sent.
		this.mapOfMessagesToSendQueue.get(clientID).add(response);
		notifySender(clientID);
	}

	private void notifySender(int clientID) {
		Semaphore semaphore = this.mapOfClientSemaphores.get(clientID);
		if (semaphore == null) {
			throw new RuntimeException("Connection has been closed for client id : " + clientID);
		}
		// Sends a message to the sender thread to wake him up !
		semaphore.release();
	}
	
	int getClientIDOfCurrentConnectionReceiver() {
		int currClientID = ConnectionReceiveMessages.clientIDThreadLocal.get();
		LOGGER.trace("Current thread clientID : " + currClientID);
		return currClientID;
	}

	private static final class ConnectionReceiveMessages implements Runnable {

		private final Socket socket;
		private final Delegate delegate;
		
		//thread local to be able to retrieve the clientID value from a ConnectionReceiveMessages thread.
		private static ThreadLocal<Integer> clientIDThreadLocal = new ThreadLocal<>();
		
		private final int clientID;
		private final ProcBridgeServer server;

		ConnectionReceiveMessages(Socket socket, int clientID, ProcBridgeServer server) {
			this.socket = socket;
			this.delegate = server.delegate;
			this.server = server;
			this.clientID = clientID; 
		}

		@Override
		public void run() {

			clientIDThreadLocal.set(clientID);
			Thread.currentThread().setName("pb_MessageReceiverThread_" + server.getPort() + "_" + clientID);

			InputStream is;
			try {
				is = socket.getInputStream();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			while (true) {
				if (!server.started || socket.isClosed() || socket.isInputShutdown()) {
					return;
				}

				try {
					// Read will be a blocking operation until next content in the is.
					RequestDecoder decoder = Protocol.read(is).asRequest();
					if (decoder == null) {
						throw ProcBridgeException.malformedInputData();
					}

					final String api = decoder.api;
					final JsonObject body = decoder.body;

					LOGGER.trace(clientID + " - Received message for api : " + api);

					if (Protocol.CLOSE_MESSAGE_API.equals(api)) {
						closeSocket();
						return;
					}
					
					try {
						//protocol specific message sending.
						if (Protocol.GET_CLIENT_ID_API.equals(api)) {
							JsonObject response = new JsonObject();
							response.addProperty("clientID", clientID);
							server.sendMessage(clientID, response);
							continue; //Next message
						}
						
						//normal message sending : 
						delegate.onMessage(api, body);
					} catch (Exception ex) {
						if (LOGGER.isDebugEnabled()) {
							LOGGER.debug("onError : " + ex, ex);
						}
						delegate.onError(ex);
					}

				} catch (ProcBridgeException e) {
					if (isConnectionReset(e)) {
						LOGGER.warn("Connection has been reset by the client without sending a close message.");
						closeSocket();
						return;
					}
					throw new RuntimeException(e);
				}

			}

		}

		private void closeSocket() {
			try {
				socket.close();
			} catch (IOException ignore) {
				// ignore it.
			}
			// notify sender in order to end the receiver thread.
			server.notifySender(clientID);
			server.mapOfClientSenders.remove(clientID);
			server.mapOfClientSemaphores.remove(clientID);
			server.mapOfMessagesToSendQueue.remove(clientID);
			clientIDThreadLocal.remove();
		}

		/**
		 * Detects a connection reset error raised by the JDK.
		 * 
		 * @param e
		 *            the exception to check recursively
		 * @return true if found.
		 */
		private boolean isConnectionReset(Throwable e) {
			if (e instanceof SocketException && "Connection reset".equals(e.getMessage())) {
				return true;
			}
			if (e.getCause() != null) {
				return isConnectionReset(e.getCause());
			}
			return false;
		}

	}

	private static final class ConnectionSendMessages implements Runnable {

		private final Socket socket;
		private final OutputStream os;

		private final int clientID;
		
		private final ProcBridgeServer server;

		ConnectionSendMessages(Socket socket, int clientID, ProcBridgeServer server) {
			this.socket = socket;
			this.clientID = clientID;
			this.server = server;
			try {
				os = socket.getOutputStream();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void run() {

			Thread.currentThread().setName("pb_MessageSenderThread_" + server.getPort() + "_" + clientID);
			while (true) {
				if (!server.started || socket.isOutputShutdown() || socket.isClosed()) {
					return;
				}

				// sends the messages effectively
				sendMessages();

				// Waits for a notification before going to the next loop of message sending.
				try {
					Semaphore semaphore = server.mapOfClientSemaphores.get(clientID);
					semaphore.acquire();
				} catch (InterruptedException ignored) {
					// ignore it.
				}
			}
		}

		private void sendMessages() {

			 ConcurrentLinkedQueue<JsonObject> messagesToSendQueue = server.mapOfMessagesToSendQueue.get(clientID);
			
			while (messagesToSendQueue!= null && !messagesToSendQueue.isEmpty()) {
				JsonObject messageToSend = messagesToSendQueue.poll();
				try {
					Protocol.write(os, new GoodResponseEncoder(messageToSend));
				} catch (ProcBridgeException e) {
					throw new RuntimeException(e);
				}
			}
		}

	}

}
