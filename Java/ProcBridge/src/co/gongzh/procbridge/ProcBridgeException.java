package co.gongzh.procbridge;

/**
 * @author Gong Zhang
 */
public class ProcBridgeException extends Exception {

    static ProcBridgeException unexpectedEndOfStream() {
        return new ProcBridgeException("unexpected end of stream");
    }

    static ProcBridgeException malformedInputData() {
        return new ProcBridgeException("malformed input data");
    }

    static ProcBridgeException malformedResponse() {
        return new ProcBridgeException("malformed response");
    }

    static ProcBridgeException incompatibleVersion() {
        return new ProcBridgeException("incompatible protocol version");
    }

    static ProcBridgeException timeout() {
        return new ProcBridgeException("timeout");
    }

    ProcBridgeException() {
        super();
    }

    ProcBridgeException(String message) {
        super(message);
    }

    ProcBridgeException(String message, Throwable cause) {
        super(message, cause);
    }

    ProcBridgeException(Throwable cause) {
        super(cause);
    }

}
