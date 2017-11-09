package de.gekko.websocket;

public interface Updateable {
	
	public void receiveUpdate(OrderBookUpdate orderBookUpdate);
}
