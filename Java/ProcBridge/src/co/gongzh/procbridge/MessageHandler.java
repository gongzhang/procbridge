package co.gongzh.procbridge;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

public interface MessageHandler {

	 public void onMessage(@NotNull JsonObject message);
	 
	 public void onError(ProcBridgeException e);
}
