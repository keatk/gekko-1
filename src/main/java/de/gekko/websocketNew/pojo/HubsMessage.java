package de.gekko.websocketNew.pojo;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class HubsMessage {
	
	@SerializedName("H")
	private String hubName;
	
	@SerializedName("M")
	private String methodName;
	
	@SerializedName("A")
	private List<String> arguments;
	
	@SerializedName("S")
	private Map<String, String> state;
	
	@SerializedName("I")
	private int invocationIdentifier;

	public String getHubName() {
		return hubName;
	}

	public void setHubName(String hubName) {
		this.hubName = hubName;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public void setArguments(List<String> arguments) {
		this.arguments = arguments;
	}

	public Map<String, String> getState() {
		return state;
	}

	public void setState(Map<String, String> state) {
		this.state = state;
	}

	public int getInvocationIdentifier() {
		return invocationIdentifier;
	}

	public void setInvocationIdentifier(int invocationIdentifier) {
		this.invocationIdentifier = invocationIdentifier;
	}

	
}
