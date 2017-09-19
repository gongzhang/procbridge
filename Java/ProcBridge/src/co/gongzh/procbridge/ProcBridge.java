package co.gongzh.procbridge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * @author Gong Zhang
 */
public final class ProcBridge {

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

    public JSONObject request(@NotNull String api) throws ProcBridgeException {
        return request(api, (JSONObject) null);
    }

    public JSONObject request(@NotNull String api, @Nullable String jsonText) throws ProcBridgeException {
        try {
            if (jsonText == null) {
                jsonText = "{}";
            }
            JSONObject obj = new JSONObject(jsonText);
            return request(api, obj);
        } catch (JSONException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @NotNull
    public JSONObject request(@NotNull String api, @Nullable JSONObject body) throws ProcBridgeException {
        final RequestEncoder request = new RequestEncoder(api, body);

        final JSONObject[] out_json = { null };
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
