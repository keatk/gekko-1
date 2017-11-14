package de.gekko.websocketNew;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.gekko.websocketNew.pojo.PersistentConnectionMessage;

public class BittrexWebsocketClientEndpoint extends Endpoint {
	
	private Gson gson = new GsonBuilder()
			// .setPrettyPrinting()
			.create();
	
	@Override
	public void onOpen(Session session, EndpointConfig config) {
		System.out.println("Connected to server");
		BittrexWebsocket.messageLatch.countDown();
		final RemoteEndpoint remote = session.getBasicRemote();
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String messageString) {
				System.out.println(messageString);
				PersistentConnectionMessage message = gson.fromJson(messageString, PersistentConnectionMessage.class);
				
				if(message.getTransportStartFlag() == 1) {
					System.out.println("YEEEEEE");
				}
			}
		});
	}

}
