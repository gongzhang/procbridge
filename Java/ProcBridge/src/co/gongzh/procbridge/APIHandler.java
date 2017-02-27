package co.gongzh.procbridge;

import java.lang.annotation.*;

/**
 * @author Gong Zhang
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface APIHandler {
}
