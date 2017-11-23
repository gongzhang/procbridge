package co.gongzh.procbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Gong Zhang
 */
final class Protocol {

    private static final byte[] FLAG = { 'p', 'b' };
    private static final byte[] VERSION = { 1, 0 };
    private static final JsonParser parser = new JsonParser();

    enum StatusCode {
        REQUEST(0), RESPONSE_GOOD(1), RESPONSE_BAD(2);
        int rawValue;
        StatusCode(int rawValue) {
            this.rawValue = rawValue;
        }
        @Nullable
        static StatusCode fromRawValue(int rawValue) {
            for (StatusCode sc : StatusCode.values()) {
                if (sc.rawValue == rawValue) {
                    return sc;
                }
            }
            return null;
        }
        @NotNull
        Decoder makeDecoder() throws ProcBridgeException {
            switch (this) {
                case REQUEST: return new RequestDecoder();
                case RESPONSE_GOOD: return new GoodResponseDecoder();
                case RESPONSE_BAD: return new BadResponseDecoder();
                default: throw new InternalError("unknown status code");
            }
        }
    }

    static final String KEY_API = "api";
    static final String KEY_BODY = "body";
    static final String KEY_MESSAGE = "msg";

    static void write(OutputStream stream, Encoder encoder) throws ProcBridgeException {
        try {
            // 1. FLAG 'p', 'b'
            stream.write(FLAG);

            // 2. VERSION
            stream.write(VERSION);

            // 3. STATUS CODE
            stream.write(encoder.getStatusCode().rawValue);

            // 4. RESERVED BYTES (2 bytes)
            stream.write(0);
            stream.write(0);

            // make json object
            byte[] data = encoder.encode();

            // 5. LENGTH (4-byte, little endian)
            int len = data.length;
            int b0 = len & 0xff;
            int b1 = (len & 0xff00) >> 8;
            int b2 = (len & 0xff0000) >> 16;
            int b3 = (len & 0xff000000) >> 24;
            stream.write(b0);
            stream.write(b1);
            stream.write(b2);
            stream.write(b3);

            // 6. JSON OBJECT
            stream.write(data);

            stream.flush();
        } catch (IOException e) {
            throw new ProcBridgeException(e);
        }
    }

    static Decoder read(InputStream stream) throws ProcBridgeException {
        try {
            int b;

            // 1. FLAG
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            if (b != FLAG[0]) throw ProcBridgeException.malformedInputData();
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            if (b != FLAG[1]) throw ProcBridgeException.malformedInputData();

            // 2. VERSION
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            if (b != VERSION[0]) throw ProcBridgeException.incompatibleVersion();
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            if (b != VERSION[1]) throw ProcBridgeException.incompatibleVersion();

            // 3. STATUS CODE
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            StatusCode statusCode = StatusCode.fromRawValue(b);
            if (statusCode == null) {
                throw ProcBridgeException.malformedInputData();
            }
            Decoder decoder = statusCode.makeDecoder();

            // 4. RESERVED BYTES (2 bytes)
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();

            // 5. LENGTH (little endian)
            int len;
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            len = b;
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            len |= (b << 8);
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            len |= (b << 16);
            b = stream.read();
            if (b == -1) throw ProcBridgeException.unexpectedEndOfStream();
            len |= (b << 24);

            if (len <= 0) {
                throw ProcBridgeException.malformedInputData();
            }

            // 6. JSON OBJECT
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
                if (buffer.size() >= len) {
                    break;
                }
            }

            if (buffer.size() != len) {
                throw ProcBridgeException.malformedInputData();
            }

            buffer.flush();
            data = buffer.toByteArray();
            String jsonText = new String(data, "UTF-8");
            JsonObject obj = parser.parse(jsonText).getAsJsonObject();

            decoder.decode(obj);
            return decoder;

        } catch (IOException e) {
            throw new ProcBridgeException(e);
        } catch (JsonParseException e) {
            throw ProcBridgeException.malformedInputData();
        }
    }

}

abstract class Encoder {
    abstract byte[] encode() throws ProcBridgeException;
    abstract Protocol.StatusCode getStatusCode();
}

final class RequestEncoder extends Encoder {

    @NotNull
    private final String api;
    @Nullable
    private final JsonObject body;

    RequestEncoder(@NotNull String api, @Nullable JsonObject body) {
        if (api.isEmpty()) {
            throw new IllegalArgumentException("api cannot be empty");
        }
        this.api = api;
        this.body = body;
    }

    @Override
    byte[] encode() throws ProcBridgeException {
        JsonObject obj = new JsonObject();
        obj.addProperty(Protocol.KEY_API, api);
        if (body != null) {
            obj.add(Protocol.KEY_BODY, body);
        } else {
            obj.add(Protocol.KEY_BODY, new JsonObject());
        }
        String jsonText = obj.toString();
        try {
            return jsonText.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ProcBridgeException(e);
        }
    }

    @Override
    Protocol.StatusCode getStatusCode() {
        return Protocol.StatusCode.REQUEST;
    }
}

final class GoodResponseEncoder extends Encoder {

    @Nullable
    private final JsonObject body;

    GoodResponseEncoder(@Nullable JsonObject body) {
        this.body = body;
    }

    @Override
    byte[] encode() throws ProcBridgeException {
        JsonObject obj = new JsonObject();
        if (body != null) {
            obj.add(Protocol.KEY_BODY, body);
        }
        String jsonText = obj.toString();
        try {
            return jsonText.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ProcBridgeException(e);
        }
    }

    @Override
    Protocol.StatusCode getStatusCode() {
        return Protocol.StatusCode.RESPONSE_GOOD;
    }

}

final class BadResponseEncoder extends Encoder {

    @Nullable
    private final String message;

    BadResponseEncoder(@Nullable String message) {
        this.message = message;
    }

    @Override
    byte[] encode() throws ProcBridgeException {
        JsonObject obj = new JsonObject();
        if (message != null) {
            obj.addProperty(Protocol.KEY_MESSAGE, message);
        }
        String jsonText = obj.toString();
        try {
            return jsonText.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ProcBridgeException(e);
        }
    }

    @Override
    Protocol.StatusCode getStatusCode() {
        return Protocol.StatusCode.RESPONSE_BAD;
    }

}

abstract class Decoder {

    abstract void decode(JsonObject object) throws ProcBridgeException;
    abstract JsonObject getResponseBody();
    abstract String getErrorMessage();
    abstract RequestDecoder asRequest();

}

final class RequestDecoder extends Decoder {

    @NotNull
    String api = "";
    @NotNull
    JsonObject body = new JsonObject();

    @Override
    void decode(JsonObject object) throws ProcBridgeException {
        try {
            String api = object.get(Protocol.KEY_API).getAsString();
            if (api.isEmpty()) {
                throw ProcBridgeException.malformedInputData();
            }
            this.api = api;

            JsonObject body = object.get(Protocol.KEY_BODY).getAsJsonObject();
            if (body != null) {
                this.body = body;
            }

        } catch (JsonParseException ex) {
            throw ProcBridgeException.malformedInputData();
        }
    }

    JsonObject getResponseBody() {
        return null;
    }

    String getErrorMessage() {
        return null;
    }

    @Override
    RequestDecoder asRequest() {
        return this;
    }
}

final class GoodResponseDecoder extends Decoder {

    @NotNull
    private
    JsonObject body = new JsonObject();

    @Override
    void decode(JsonObject object) throws ProcBridgeException {
        JsonObject body = object.get(Protocol.KEY_BODY).getAsJsonObject();
        if (body != null) {
            this.body = body;
        }
    }

    JsonObject getResponseBody() {
        return body;
    }

    String getErrorMessage() {
        return null;
    }

    @Override
    RequestDecoder asRequest() {
        return null;
    }
}

final class BadResponseDecoder extends Decoder {

    @NotNull
    private
    String message = "";

    @Override
    void decode(JsonObject object) throws ProcBridgeException {
        String msg = object.get(Protocol.KEY_MESSAGE).getAsString();
        if (msg != null) {
            this.message = msg;
        }
    }

    JsonObject getResponseBody() {
        return null;
    }

    String getErrorMessage() {
        return message;
    }

    @Override
    RequestDecoder asRequest() {
        return null;
    }
}
