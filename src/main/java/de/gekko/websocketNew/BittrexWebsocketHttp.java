package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.gekko.websocketNew.pojo.Hub;
import de.gekko.websocketNew.pojo.HubMessage;
import de.gekko.websocketNew.pojo.NegotiationResponse;

public class BittrexWebsocketHttp {
	
	/* constants */

	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocketHttp.class);

	
	/* variables */
	
	private Gson gson;
	private HttpClient httpClient;
	private CookieStore cookieStore;
	private ArrayList<Hub> hubs;
	private Map<String, Session> webSocketSessions;
	
	/* constructors */
	
	public BittrexWebsocketHttp(HttpClient httpClient, CookieStore cookieStore) {
		this.httpClient = httpClient;
		this.cookieStore = cookieStore;
		this.gson = new GsonBuilder()
				// .setPrettyPrinting()
				.create();
		this.hubs = new ArrayList<>();
		this.webSocketSessions = new HashMap<>();
	}
	
	/* public methods */

	public void subscribeToOrderbook(String currencyPair) throws ClientProtocolException, URISyntaxException, IOException, InterruptedException {
		// Check if already subscribed
		if(webSocketSessions.containsKey(currencyPair)) {
			LOGGER.info("Already subscribed to {}.", currencyPair);
			return;
		}
		
		// Negotiate new connection with server
		NegotiationResponse negotiationResponse  = negotiate();
		if(negotiationResponse == null) {
			LOGGER.info("ERROR: Negotiation with server failed while subscribing to {}.", currencyPair);
		}
		
		// Create new websocket session for subscription 
		Session webSocketSession = connect(negotiationResponse);
		if(webSocketSession == null) {
			LOGGER.info("ERROR: Could not create websocket session while subscribing to {}.", currencyPair);
		} else {
			start(negotiationResponse);
		}
		
		// Subscribe to updates
		subscribe(currencyPair, webSocketSession);
		webSocketSessions.put(currencyPair, webSocketSession);
	}
	
	
	/**
	 * Register custom hub. Currently not used.
	 * @param hubName
	 */
	public void registerHub(String hubName) {
		Hub hub = new Hub();
		hub.setName(hubName);
		hubs.add(hub);
	}
	
	/* private methods */
	
	/**
	 * Sends signalR negotiation request and sets negotiationResponse variable.
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private NegotiationResponse negotiate() throws URISyntaxException, ClientProtocolException, IOException {
		// If no custom hub was set use default hub
		if (hubs.isEmpty()) {
			registerHub(BittrexWebsocket.DEFAULT_HUB);
		}

		// Build negotiation request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(BittrexWebsocket.DEFAULT_SERVER_DOMAIN).setPath("/signalr/negotiate")
				.setParameter("clientProtocol", BittrexWebsocket.CLIENT_PROTOCOL_NUMBER)
				.setParameter("connectionData", gson.toJson(hubs));
		HttpGet request = new HttpGet(builder.build());

		// Send negotiation request
		LOGGER.info("Sending negotiation request...");
		HttpResponse response = httpClient.execute(request);

		// Read and set negotiation response
		String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8.name());
//		System.out.println(responseContent);
		NegotiationResponse negotiationResponse = null;
		try {
			negotiationResponse = new Gson().fromJson(responseContent, NegotiationResponse.class);
		} catch(Throwable t) {
			LOGGER.info("NEGOTIATION ERROR: " + t.toString());
			LOGGER.info(responseContent);
		}

		LOGGER.info("Negotiation response received.");
		return negotiationResponse;
	}
	
	/**
	 * Starts the websocket transport.
	 * @throws URISyntaxException
	 */
	private Session connect(NegotiationResponse negotiationResponse) throws URISyntaxException {
		// Create cookie strings for websocket connection if cloudflare DDOS protection is enabled
		ArrayList<String> cookies = new ArrayList<>();
		cookieStore.getCookies().forEach((item) -> {
			if(item.getName().equals("__cfduid") || item.getName().equals("cf_clearance")) {
				String cookieString = item.getName() + "=" + item.getValue();
				cookies.add(cookieString);
			}
		});
		
		
		// Create client config with cookies and user agent
		ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
			@Override
			public void beforeRequest(Map<String, List<String>> headers) {
				headers.put("User-Agent", Arrays.asList(BittrexWebsocket.USER_AGENTS[0]));
				headers.put("Cookie", cookies);
			}
		}).build();

		// Build connection request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("wss").setHost(BittrexWebsocket.DEFAULT_SERVER_DOMAIN).setPath("/signalr/connect")
				.setParameter("transport", "webSockets").setParameter("clientProtocol", BittrexWebsocket.CLIENT_PROTOCOL_NUMBER)
				.setParameter("connectionToken", negotiationResponse.getConnectionToken())
				.setParameter("connectionData", gson.toJson(hubs))
				.setParameter("tid", "" + (new Random().nextInt(12) + 1));

//		System.out.println(builder.build().toString());

		LOGGER.info("Starting websocket transport...");
		Session webSocketSession = null;
		try {
			// Connect websocket to server
			WebSocketContainer container = ContainerProvider.getWebSocketContainer();
			container.setDefaultMaxTextMessageBufferSize(1048576);
			webSocketSession = container.connectToServer(BittrexWebsocketClientEndpoint.class, cec, builder.build());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			//startupLatch.await(100, TimeUnit.SECONDS);
		} catch (DeploymentException | IOException ex) {
			// Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
		}
		LOGGER.info("Websocket transport started.");
		return webSocketSession;
	}
	
	/**
	 * Sends start signal to server. To be used after websocket transport was started.
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private void start(NegotiationResponse negotiationResponse) throws URISyntaxException, ClientProtocolException, IOException {
		// Build negotiation request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(BittrexWebsocket.DEFAULT_SERVER_DOMAIN).setPath("/signalr/start")
		.setParameter("transport", "webSockets").setParameter("clientProtocol", BittrexWebsocket.CLIENT_PROTOCOL_NUMBER)
		.setParameter("connectionToken", negotiationResponse.getConnectionToken())
		.setParameter("connectionData", gson.toJson(hubs));
		HttpGet request = new HttpGet(builder.build());

		// Send negotiation request
		LOGGER.info("Sending transport start notification...");
		HttpResponse response = httpClient.execute(request);

		// Read and set negotiation response
		String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8.name());
		if(gson.fromJson(responseContent, JsonObject.class).get("Response").toString().equals("\"started\"")) {
			LOGGER.info("Start notification successful.");
		} else {
			LOGGER.info("ERROR: Start notification failed.");
		}
	}

	private void subscribe(String currencyPair, Session webSocketSession) throws IOException, InterruptedException {
		LOGGER.info("Subscribing to [{}].", currencyPair);
		// Prepare and send subscription message
		HubMessage subscriptionMessage = new HubMessage();
		subscriptionMessage.setHubName(BittrexWebsocket.DEFAULT_HUB);
		subscriptionMessage.setMethodName("SubscribeToExchangeDeltas");
		subscriptionMessage.setArguments(Arrays.asList(currencyPair));
		webSocketSession.getBasicRemote().sendText(gson.toJson(subscriptionMessage));

		// Wait for response
		LOGGER.info("Waiting for subscription response for [{}].", currencyPair);
		LOGGER.info("Querying exchange state for [{}].", currencyPair);
		// Prepare and send exchange state request

		HubMessage exchangeStateRequest = new HubMessage();
		exchangeStateRequest.setHubName(BittrexWebsocket.DEFAULT_HUB);
		exchangeStateRequest.setMethodName("QueryExchangeState");
		exchangeStateRequest.setArguments(Arrays.asList(currencyPair));
		exchangeStateRequest.setInvocationIdentifier(1);
		webSocketSession.getBasicRemote().sendText(gson.toJson(exchangeStateRequest));

		LOGGER.info("EXITING subscribe " + currencyPair);
	}

}
