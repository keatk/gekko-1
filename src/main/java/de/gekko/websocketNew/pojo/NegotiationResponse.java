package de.gekko.websocketNew.pojo;

import com.google.gson.annotations.SerializedName;

/**
 * NegotiationResponse POJO class
 * JSON:
 * { 
 * 	"Url":"/signalr",
 *  "ConnectionToken":"X97dw3uxW4NPPggQsYVcNcyQcuz4w2",
 * 	"ConnectionId":"05265228-1e2c-46c5-82a1-6a5bcc3f0143",
 * 	"KeepAliveTimeout":10.0,
 *  "DisconnectTimeout":5.0,
 *  "TryWebSockets":true,
 *  "ProtocolVersion":"1.5",
 *  "TransportConnectTimeout":30.0, 
 *  "LongPollDelay":0.0
 * }
 *  @author Maximilian Pfister
 */
public class NegotiationResponse {
	
	@SerializedName("Url")
	private String url;
	
	@SerializedName("ConnectionToken")
	private String connectionToken;
	
	@SerializedName("ConnectionId")
	private String connectionId;
	
	@SerializedName("KeepAliveTimeout")
	private double keepAliveTimeout;
	
	@SerializedName("DisconnectTimeout")
	private double disconnectTimeout;
	
	@SerializedName("TryWebSockets")
	private boolean tryWebSockets;
	
	@SerializedName("ProtocolVersion")
	private String protocolVersion;
	
	@SerializedName("TransportConnectTimeout")
	private double transportConnectTimeout;
	
	@SerializedName("LongPollDelay")
	private double longPollDelay;

	public String getUrl() {
		return url;
	}

	public String getConnectionToken() {
		return connectionToken;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public double getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	public double getDisconnectTimeout() {
		return disconnectTimeout;
	}

	public boolean isTryWebSockets() {
		return tryWebSockets;
	}

	public String getProtocolVersion() {
		return protocolVersion;
	}

	public double getTransportConnectTimeout() {
		return transportConnectTimeout;
	}

	public double getLongPollDelay() {
		return longPollDelay;
	}

}
