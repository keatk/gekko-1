package de.gekko.websocketNew;

import java.lang.reflect.Type;
import java.util.List;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.gekko.websocketNew.pojo.ExchangeStateUpdate;
import de.gekko.websocketNew.pojo.HubMessage;
import de.gekko.websocketNew.pojo.PersistentConnectionMessage;
import de.gekko.websocketNew.pojo.ResponseMessage;
/**
 * Handles Bittrex websocket transport.
 * @author Maximilian Pfister
 *
 */
public class BittrexWebsocketClientEndpoint extends Endpoint {

	public static boolean LOG = false;

	/* constants */

	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocketClientEndpoint.class);

	/* variables */

	private Gson gson = new GsonBuilder()
			// .setPrettyPrinting()
			.create();
	private BittrexWebsocket bittrexWebsocket = BittrexWebsocket.getInstance();
	private boolean startupSuccess = false;

	/* public methods */

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		System.out.println("Connected to server");
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String messageString) {
					// Check if keep alive message
					if (messageString.length() < 3) {
						LOGGER.info("KeepAliveMessage");
						// do nothing
						return;
					}
					// Check if PersistentConnectionMessage
					if (messageString.charAt(2) == 'C') {
//						LOGGER.info("PersistentConnectionMessage");
						PersistentConnectionMessage message = gson.fromJson(messageString, PersistentConnectionMessage.class);
						
						// Check for startup message
						if (!startupSuccess) {
							if (message.getTransportStartFlag() == 1) {
//								bittrexWebsocket.getStartupLatch().countDown();
								return;
							}
						}
						
						// Check if hub messages are present
						if(!message.getMessageData().isEmpty()) {
							try {
								JsonArray hubMessages = gson.fromJson(message.getMessageData().toString(), JsonArray.class);
								hubMessages.forEach((jsonElement) ->{
									// Check each hub message for exchange state updates
									JsonObject jsonObject = jsonElement.getAsJsonObject();
									if(jsonObject.get("M").getAsString().equals("updateExchangeState")) {
										// Get exchange state items
										JsonArray exchangeStates = gson.fromJson(jsonObject.get("A").toString(), JsonArray.class);
										exchangeStates.forEach((exchangeStateElement)->{
											ExchangeStateUpdate exchangeState = gson.fromJson(exchangeStateElement.toString(), ExchangeStateUpdate.class);
											bittrexWebsocket.sendToChannelHandler(bittrexWebsocket.toCurrencyPair(exchangeState.getMarketName()), exchangeState);
										});
									} else {
										// do something with other methods
									}
								});
								return;
							} catch (Exception e) {
								LOGGER.info(message.getMessageData().toString());
								LOGGER.info(e.toString());
								System.exit(1);
							}
							
//							if(hubMessage.getMethodName().equals("updateExchangeState")) {
//							}
							
						}

					}
					// Check if responseMessage
					if (messageString.charAt(2) == 'R') {
						try {
							ResponseMessage responseMessage = gson.fromJson(messageString, ResponseMessage.class);
							if(responseMessage.getResponse().toString().equals("true")) {
								//TODO IMPLEMENT RESPONSE LOCK
								LOGGER.info("IDENTIFIER: " + responseMessage.getInvocationIdentifier().toString());
								return;
							} else {
								LOGGER.info("YAAAAS");
								ExchangeStateUpdate exchangeState = gson.fromJson(responseMessage.getResponse().toString(), ExchangeStateUpdate.class);
								return;
							}
						} catch (Throwable t) {
//							LOGGER.info("ERROR MESSAGE: " + messageString);
							LOGGER.info(t.toString());
							System.exit(1);
						}
					}
					if (messageString.charAt(2) == 'I') {
						LOGGER.info(messageString);
						JsonObject jsonOb = gson.fromJson(messageString, JsonObject.class);
						return;
					}
					LOGGER.info(messageString);
				}
			});

	}

}
