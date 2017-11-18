package de.gekko.websocketNew.pojo;

import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;

public class PersistentConnectionMessage {
	
	@SerializedName("C")
	private String messageId;
	
	@SerializedName("M")
	private JsonArray messageData;
	
	@SerializedName("S")
	private int transportStartFlag;
	
	@SerializedName("G")
	private String groupToken;

	public String getMessageId() {
		return messageId;
	}

	public JsonArray getMessageData() {
		return messageData;
	}

	public int getTransportStartFlag() {
		return transportStartFlag;
	}

	public String getGroupToken() {
		return groupToken;
	}

}
