package de.gekko.websocketNew;

public interface UpdateableOrderbook {
	
	public void receiveUpdate(OrderBookUpdate orderBookUpdate);
}
