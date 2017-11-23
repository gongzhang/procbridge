package co.gongzh.procbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gong Zhang
 */
final class ReflectiveDelegate implements ProcBridgeServer.Delegate {

    private static final JsonParser parser = new JsonParser();
    @NotNull
    private final Object target;
    @NotNull
    private final Map<String, Method> apiMap;

    ReflectiveDelegate(@NotNull Object target) {
        this.target = target;
        this.apiMap = new HashMap<>();
        for (Method m : target.getClass().getDeclaredMethods()) {
            if (m.getAnnotation(APIHandler.class) != null) {
                String api = m.getName();
                if (apiMap.containsKey(api)) {
                    throw new RuntimeException("duplicate api: " + api);
                }
                if (m.getParameterCount() == 1) {
                    Class<?> cl = m.getParameterTypes()[0];
                    if (cl != JsonObject.class) {
                        throw new RuntimeException("parameter is not a JSON object for api: " + api);
                    }
                } else if (m.getParameterCount() > 1) {
                    throw new RuntimeException("too many parameters for api: " + api);
                }
                if (m.getReturnType() != JsonObject.class &&
                        m.getReturnType() != String.class &&
                        m.getReturnType() != void.class) {
                    throw new RuntimeException("return type is not a JSON object/text for api: " + api);
                }
                m.setAccessible(true);
                apiMap.put(api, m);
            }
        }
    }

    @Override
    public @Nullable JsonObject handleRequest(@NotNull String api, @NotNull JsonObject body) throws Exception {
        Method m = apiMap.get(api);
        if (m == null) {
            throw new RuntimeException("unknown api: " + api);
        }
        Object ret;
        try {
            if (m.getParameterCount() == 1) {
                ret = m.invoke(target, body);
            } else {
                ret = m.invoke(target);
            }
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex.getTargetException().toString());
        }
        if (ret instanceof JsonObject) {
            return (JsonObject) ret;
        } else if (ret instanceof String) {
            String jsonText = (String) ret;
            return parser.parse(jsonText).getAsJsonObject();
        } else {
            return null;
        }
    }

}
