package de.gekko.websocket;

public interface UpdateableOrderbook {
	
	public void receiveUpdate(OrderBookUpdate orderBookUpdate);
}
