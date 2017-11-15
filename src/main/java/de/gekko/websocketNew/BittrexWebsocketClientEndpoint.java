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

import de.gekko.websocketNew.pojo.ExchangeState;
import de.gekko.websocketNew.pojo.HubMessage;
import de.gekko.websocketNew.pojo.PersistentConnectionMessage;
import de.gekko.websocketNew.pojo.ResponseMessage;
/**
 * Handles Bittrex websocket transport.
 * @author Maximilian Pfister
 *
 */
public class BittrexWebsocketClientEndpoint extends Endpoint {
	
	/* constants */
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocketClientEndpoint.class);
	
	/* variables */
	
	private Gson gson = new GsonBuilder()
			// .setPrettyPrinting()
			.create();
	private BittrexWebsocket bittrexWebsocket = BittrexWebsocket.getInstance();
	private boolean startupSuccess = false;
	private 	ExecutorService messageExecutor = Executors.newSingleThreadExecutor();
	
	/* public methods */
	
	@Override
	public void onOpen(Session session, EndpointConfig config) {
		System.out.println("Connected to server");
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String messageString) {

				// Handle messages concurrently
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
						PersistentConnectionMessage message = gson.fromJson(messageString, PersistentConnectionMessage.class);
						
						// Check for startup message
						if (!startupSuccess) {
							if (message.getTransportStartFlag() == 1) {
								bittrexWebsocket.getStartupLatch().countDown();
								return;
							}
						}
						
						// Check if hub messages are present
						if(!message.getMessageData().isEmpty()) {
							HubMessage hubMessage = gson.fromJson(message.getMessageData().toString(), HubMessage.class);
							if(hubMessage.getMethodName().equals(""));
							
						}

					}
					// Check if responseMessage
					if (messageString.charAt(2) == 'R') {
						try {
							ResponseMessage responseMessage = gson.fromJson(messageString, ResponseMessage.class);
							if(responseMessage.getResponse().toString().equals("true")) {
								bittrexWebsocket.getResponseLatch().countDown();
								return;
							} else {
								ExchangeState exchangeState = gson.fromJson(responseMessage.getResponse().toString(), ExchangeState.class);
								bittrexWebsocket.setExchangeState(exchangeState);
								bittrexWebsocket.getExchangeStateLatch().countDown();
								return;
							}
						} catch (Throwable t) {
							LOGGER.info(t.toString());
							LOGGER.info(messageString);
							System.exit(1);
						}
					}
					
				});
			}
		});
	}

}
