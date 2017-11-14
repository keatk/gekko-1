package de.gekko.websocketNew.pojo;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class PersistentConnectionMessage {
	
	@SerializedName("C")
	private String messageId;
	
	@SerializedName("M")
	private List<JsonObject> messageData;
	
	@SerializedName("S")
	private int transportStartFlag;
	
	@SerializedName("G")
	private String groupToken;

	public String getMessageId() {
		return messageId;
	}

	public List<JsonObject> getMessageData() {
		return messageData;
	}

	public int getTransportStartFlag() {
		return transportStartFlag;
	}

	public String getGroupToken() {
		return groupToken;
	}

}
