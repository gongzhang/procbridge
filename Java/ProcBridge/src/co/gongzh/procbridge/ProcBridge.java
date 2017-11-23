package co.gongzh.procbridge;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author Gong Zhang
 */
public final class ProcBridge {

    private static final JsonParser parser = new JsonParser();
    private final String host;
    private final int port;
    private int timeout;

    public ProcBridge(String host, int port, int timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
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

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public JsonObject request(@NotNull String api) throws ProcBridgeException {
        return request(api, (JsonObject) null);
    }

    public JsonObject request(@NotNull String api, @Nullable String jsonText) throws ProcBridgeException {
        try {
            if (jsonText == null) {
                jsonText = "{}";
            }
            JsonObject obj = parser.parse(jsonText).getAsJsonObject();
            return request(api, obj);
        } catch (JsonParseException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @NotNull
    public JsonObject request(@NotNull String api, @Nullable JsonObject body) throws ProcBridgeException {
        final RequestEncoder request = new RequestEncoder(api, body);

        final JsonObject[] out_json = { null };
        final String[] out_err_msg = { null };

        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            try (OutputStream os = socket.getOutputStream();
                 InputStream is = socket.getInputStream()) {

                Protocol.write(os, request);
                Decoder decoder = Protocol.read(is);
                out_json[0] = decoder.getResponseBody();
                if (out_json[0] == null) {
                    // must be error
                    out_err_msg[0] = decoder.getErrorMessage();
                }

            } catch (IOException | ProcBridgeException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new ProcBridgeException(e);
        }

        if (out_json[0] == null) {
            if (out_err_msg[0] == null) {
                out_err_msg[0] = "server error";
            }
            throw new ProcBridgeException(out_err_msg[0]);
        }

        assert out_json[0] != null;
        return out_json[0];
    }

}
