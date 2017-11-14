package de.gekko.websocketNew;

import java.io.IOException;
import javax.websocket.ClientEndpoint;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

public class BittrexWebsocketClientEndpoint extends Endpoint {
	
	@Override
	public void onOpen(Session session, EndpointConfig config) {
		System.out.println("Connected to server");
		BittrexWebsocket.messageLatch.countDown();
		final RemoteEndpoint remote = session.getBasicRemote();
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String text) {
				System.out.println(text);
			}
		});
	}

}
