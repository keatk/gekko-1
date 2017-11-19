package de.gekko.websocket;

import java.io.IOException;
import java.util.Arrays;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.gekko.websocket.pojo.ExchangeStateUpdate;
import de.gekko.websocket.pojo.HubMessage;
import de.gekko.websocket.pojo.PersistentConnectionMessage;
import de.gekko.websocket.pojo.ResponseMessage;

/**
 * Handles Bittrex websocket transport.
 * 
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
	private String currencyPair;
	private boolean startupSuccess = false;

	public void setCurrencyPair(String currencyPair) {
		this.currencyPair = currencyPair;
	}

	public void getExchangeState(Session session) throws IOException {
		// Prepare and send exchange state request

		HubMessage exchangeStateRequest = new HubMessage();
		exchangeStateRequest.setHubName(BittrexWebsocket.DEFAULT_HUB);
		exchangeStateRequest.setMethodName("QueryExchangeState");
		exchangeStateRequest.setArguments(Arrays.asList(currencyPair));
		exchangeStateRequest.setInvocationIdentifier(1);
		session.getBasicRemote().sendText(gson.toJson(exchangeStateRequest));
	}

	/* public methods */

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		session.addMessageHandler(new MessageHandler.Whole<String>() {
			public void onMessage(String messageString) {
				// Check if keep alive message
				if (messageString.length() < 3) {
					LOGGER.info("KeepAliveMessage");
					bittrexWebsocket.keepAliveChannelHandler(currencyPair);
					return;
				}

				/* begin persistent connection message */
				if (messageString.charAt(2) == 'C') {
					PersistentConnectionMessage message = gson.fromJson(messageString, PersistentConnectionMessage.class);

					// Check if hub messages are present
					if (message.getMessageData().size() != 0) {
						try {
							message.getMessageData().forEach((jsonElement) -> {
								// Check each hub message for exchange state updates
								JsonObject jsonObject = jsonElement.getAsJsonObject();
								if (jsonObject.get("M").getAsString().equals("updateExchangeState")) {
									// Get exchange state items
									JsonArray exchangeStates = gson.fromJson(jsonObject.get("A"), JsonArray.class);
									exchangeStates.forEach((exchangeStateElement) -> {
										ExchangeStateUpdate exchangeState = gson.fromJson(exchangeStateElement, ExchangeStateUpdate.class);
										bittrexWebsocket.sendToChannelHandler(exchangeState.getMarketName(), exchangeState);
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
					}
					
					// Check for startup message
					if (!startupSuccess) {
						if (message.getTransportStartFlag() == 1) {
							startupSuccess = true;
							return;
						}
					}
					
				}
				/* end persistent connection message */

				/* begin response message */
				if (messageString.charAt(2) == 'R') {

					try {
						ResponseMessage responseMessage = gson.fromJson(messageString, ResponseMessage.class);
						if (responseMessage.getResponse().toString().equals("true")) {
							LOGGER.info("RESPONSE RECEIVED");
							getExchangeState(session);
							return;
						} else {
							ExchangeStateUpdate exchangeState = gson.fromJson(responseMessage.getResponse(),
									ExchangeStateUpdate.class);
							bittrexWebsocket.sendToChannelHandler(currencyPair, exchangeState);
							return;
						}
					} catch (Exception e) {
						LOGGER.info(messageString);
						LOGGER.info(e.toString());
						System.exit(1);
					}
				}
				/* end response message */

				//TODO
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
