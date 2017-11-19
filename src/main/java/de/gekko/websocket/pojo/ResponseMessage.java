package de.gekko.websocket.pojo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class ResponseMessage {
	
    @SerializedName("R")
    protected JsonElement response;
    
	@SerializedName("I")
	private JsonElement invocationIdentifier;

	public JsonElement getResponse() {
		return response;
	}

	public JsonElement getInvocationIdentifier() {
		return invocationIdentifier;
	}

}
