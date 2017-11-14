package de.gekko.websocketNew;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.gekko.websocketNew.pojo.HubMessage;
import de.gekko.websocketNew.pojo.PersistentConnectionMessage;
/**
 * Handles Bittrex websocket transport.
 * @author Maximilian Pfister
 *
 */
@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576)
public class BittrexWebsocketClientEndpoint extends Endpoint {
	
	/* constants */
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocketClientEndpoint.class);
	
	/* variables */
	
	private Gson gson = new GsonBuilder()
			// .setPrettyPrinting()
			.create();
	private BittrexWebsocket bittrexWebsocket;
	private boolean startupSuccess = false;
	private 	ExecutorService messageExecutor = Executors.newSingleThreadExecutor();
	
	/* public methods */
	
	@Override
	public void onOpen(Session session, EndpointConfig config) {
		System.out.println("Connected to server");
		BittrexWebsocket.messageLatch.countDown();
		final RemoteEndpoint remote = session.getBasicRemote();
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String messageString) {

				// handle messages
				messageExecutor.submit(() -> {
					// Check if keep alive message
					if (messageString.length() < 3) {
						LOGGER.info("KeepAliveMessage");
						// do nothing
						return;
					}
					// Check if PersistentConnectionMessage
					if (messageString.charAt(2) == 'C') {
						LOGGER.info("PersistentConnectionMessage");
						// TODO
//						if (!startupSuccess) {
//							PersistentConnectionMessage message = gson.fromJson(messageString,
//									PersistentConnectionMessage.class);
//							if (message.getTransportStartFlag() == 1) {
//								startupSuccess = true;
//							}
//						}
						return;
					}
					if (messageString.charAt(2) == 'R') {
						bittrexWebsocket.responseLatch.countDown();
						return;
					}
				});
			}
		});
	}

}
