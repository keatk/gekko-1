package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.gekko.websocketNew.pojo.ExchangeStateUpdate;
import de.gekko.websocketNew.pojo.Hub;
import de.gekko.websocketNew.pojo.HubMessage;
import de.gekko.websocketNew.pojo.NegotiationResponse;

/**
 * Connects to Bittrex Websocket endpoint using the signalR protocol.
 * 
 * @author Maximilian Pfister TODO WORK IN PROGRESS
 */
public class BittrexWebsocket {

	/* constants */

	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocket.class);

	private static final String DEFAULT_SERVER_DOMAIN = "bittrex.com";
	private static final String CLIENT_PROTOCOL_NUMBER = "1.5";
	private static final String DEFAULT_HUB = "corehub";

	private static final String[] USER_AGENTS = {
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:56.0) Gecko/20100101 Firefox/56.0",
			"Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:46.0) Gecko/20100101 Firefox/46.0",
			"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:41.0) Gecko/20100101 Firefox/41.0" };
	
	private final  CountDownLatch startupLatch = new CountDownLatch(1);
	private final  CountDownLatch responseLatch = new CountDownLatch(1);
	private final  CountDownLatch exchangeStateLatch = new CountDownLatch(1);

	/* variables */
	
	private static BittrexWebsocket instance;

	private CookieStore cookieStore;
	private HttpClient httpClient;
	private Session webSocketSession;
	private Gson gson;
	private Map<CurrencyPair, ChannelHandler> channelHandlers;

	private ArrayList<Hub> hubs;

	private NegotiationResponse negotiationResponse;
	
	private ExchangeStateUpdate exchangeState;
	
	/* constructors */

	private BittrexWebsocket() {
		this.gson = new GsonBuilder()
				// .setPrettyPrinting()
				.create();
		hubs = new ArrayList<>();
		channelHandlers = new HashMap<>();
	}

	/* public methods */
	
	//TODO remove after class is finished
	public static void main(String[] args)
			throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		BittrexWebsocket bittrexWebsocket = new BittrexWebsocket();
		bittrexWebsocket.init();
		bittrexWebsocket.subscribeOrderbook(new CurrencyPair(Currency.getInstance("USDT"), Currency.getInstance("BTC")));
	}

	/**
	 * Returns singleton instance.
	 * @return
	 */
	public static synchronized BittrexWebsocket getInstance() {
		if (BittrexWebsocket.instance == null) {
			BittrexWebsocket.instance = new BittrexWebsocket();
		}
		return BittrexWebsocket.instance;
	}
	
	public ExchangeStateUpdate getExchangeState() {
		return exchangeState;
	}

	public void setExchangeState(ExchangeStateUpdate exchangeState) {
		this.exchangeState = exchangeState;
	}

	public CountDownLatch getStartupLatch() {
		return startupLatch;
	}

	public CountDownLatch getResponseLatch() {
		return responseLatch;
	}

	public CountDownLatch getExchangeStateLatch() {
		return exchangeStateLatch;
	}
	
	/**
	 * Converts currencyPair to bittrex currency string used in websocket messages.
	 * @param currencyPair
	 * @return
	 */
	public String toBittrexCurrencyString(CurrencyPair currencyPair) {
		return currencyPair.base.toString() + "-" + currencyPair.counter.toString();
	}
	
	/**
	 * Converts bittrex currency string to currencyPair.
	 * @param currencyPair
	 * @return
	 */
	public CurrencyPair toCurrencyPair(String bittrexCurrencyString) {
		String currencies[] = bittrexCurrencyString.split("-");
		return new CurrencyPair(Currency.getInstance(currencies[0]), Currency.getInstance(currencies[1]));
	}

	/**
	 * Initializes websocket connection to Bittrex.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	public void init() throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		LOGGER.info("Initalizing connection...");

		cookieStore = new BasicCookieStore();
		httpClient = HttpClients.custom().setUserAgent(USER_AGENTS[0]).setDefaultCookieStore(cookieStore).build();
		CloudflareScraper scraper = new CloudflareScraper(httpClient);

		// Check for Cloudflare DDOS protection
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(DEFAULT_SERVER_DOMAIN);

		HttpGet httpGet = new HttpGet(builder.build());
		HttpResponse response = httpClient.execute(httpGet);
		if (response.getStatusLine().getStatusCode() == 503) {
			// Bypass Cloudflare DDOS protection
			scraper.checkAndSolve(response, DEFAULT_SERVER_DOMAIN);
		}
		negotiate();
		connect();
		start();
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
	
	/**
	 * Subscribe to orderbook related to currencyPair.
	 * @param currencyPair
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public synchronized void subscribeOrderbook(CurrencyPair currencyPair) throws IOException, InterruptedException {
		subscribe(currencyPair);
		if(exchangeState == null) {
			LOGGER.info("ERROR: Cloud not retrieve exchange state.");
		}
		sendToChannelHandler(currencyPair, exchangeState);
		// Reset exchange state for next subscribtion 
		exchangeState = null;
	}
	
	/**
	 * Sends update to channelHandler.
	 * @param currencyPair
	 * @param update
	 */
	public void sendToChannelHandler(CurrencyPair currencyPair, ChannelHandlerUpdate update) {
		if(!channelHandlers.containsKey(currencyPair)) {
			createChannelHandler(currencyPair);
		}
		channelHandlers.get(currencyPair).feedUpdate(update);
	}

	/* private methods */
	
	/**
	 * Creates new ChannelHandler.
	 * @param currencyPair
	 */
	private synchronized void createChannelHandler(CurrencyPair currencyPair) {
		if(!channelHandlers.containsKey(currencyPair)) {
			channelHandlers.put(currencyPair, ChannelHandler.createInstance());
		}
	}

	/**
	 * Sends signalR negotiation request and sets negotiationResponse variable.
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private void negotiate() throws URISyntaxException, ClientProtocolException, IOException {
		// If no custom hub was set use default hub
		if (hubs.isEmpty()) {
			registerHub(DEFAULT_HUB);
		}

		// Build negotiation request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(DEFAULT_SERVER_DOMAIN).setPath("/signalr/negotiate")
				.setParameter("clientProtocol", CLIENT_PROTOCOL_NUMBER)
				.setParameter("connectionData", gson.toJson(hubs));
		HttpGet request = new HttpGet(builder.build());

		// Send negotiation request
		LOGGER.info("Sending negotiation request...");
		HttpResponse response = httpClient.execute(request);

		// Read and set negotiation response
		String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8.name());
//		System.out.println(responseContent);
		try {
			negotiationResponse = new Gson().fromJson(responseContent, NegotiationResponse.class);
		} catch(Throwable t) {
			LOGGER.info("NEGOTIATION ERROR: " + t.toString());
			LOGGER.info(responseContent);
		}

		LOGGER.info("Negotiation response received.");
	}

	/**
	 * Starts the websocket transport.
	 * @throws URISyntaxException
	 */
	private void connect() throws URISyntaxException {
		// Create cookie strings for websocket connection if cloudflare DDOS protection is enabled
		ArrayList<String> cookies = new ArrayList<>();
		cookieStore.getCookies().forEach((item) -> {
			if(item.getName().equals("__cfduid") || item.getName().equals("cf_clearance")) {
				String cookieString = item.getName() + "=" + item.getValue();
				cookies.add(cookieString);
//				System.out.println(cookieString);
			}
		});
		
		// Create client config with cookies and user agent
		ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
			@Override
			public void beforeRequest(Map<String, List<String>> headers) {
				headers.put("User-Agent", Arrays.asList(USER_AGENTS[0]));
				headers.put("Cookie", cookies);
			}
		}).build();

		// Build connection request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("wss").setHost(DEFAULT_SERVER_DOMAIN).setPath("/signalr/connect")
				.setParameter("transport", "webSockets").setParameter("clientProtocol", CLIENT_PROTOCOL_NUMBER)
				.setParameter("connectionToken", negotiationResponse.getConnectionToken())
				.setParameter("connectionData", gson.toJson(hubs));
//				.setParameter("tid", "" + (new Random().nextInt(12) + 1));

//		System.out.println(builder.build().toString());

		LOGGER.info("Starting websocket transport...");
		try {
			// Connect websocket to server
			WebSocketContainer container = ContainerProvider.getWebSocketContainer();
			container.setDefaultMaxTextMessageBufferSize(1048576);
			webSocketSession = container.connectToServer(BittrexWebsocketClientEndpoint.class, cec, builder.build());
			//startupLatch.await(100, TimeUnit.SECONDS);
		} catch (DeploymentException | IOException ex) {
			// Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
		}
		LOGGER.info("Websocket transport started.");
	}
	
	/**
	 * Sends start signal to server. To be used after websocket transport was started.
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private void start() throws URISyntaxException, ClientProtocolException, IOException {
		// Build negotiation request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(DEFAULT_SERVER_DOMAIN).setPath("/signalr/start")
		.setParameter("transport", "webSockets").setParameter("clientProtocol", CLIENT_PROTOCOL_NUMBER)
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
	
	private void subscribe(CurrencyPair currencyPair) throws IOException, InterruptedException {
		LOGGER.info("Subscribing to [{}].", currencyPair);
		// Prepare and send subscription message
		HubMessage subscriptionMessage = new HubMessage();
		subscriptionMessage.setHubName(DEFAULT_HUB);
		subscriptionMessage.setMethodName("SubscribeToExchangeDeltas");
		subscriptionMessage.setArguments(Arrays.asList(toBittrexCurrencyString(currencyPair)));
		webSocketSession.getBasicRemote().sendText(gson.toJson(subscriptionMessage));
		
		// Wait for response
		LOGGER.info("Waiting for subscription response for [{}].", currencyPair);
		responseLatch.await(100, TimeUnit.SECONDS);
		LOGGER.info("Querying exchange state for [{}].", currencyPair);
		// Prepare and send exchange state request
		HubMessage exchangeStateRequest = new HubMessage();
		exchangeStateRequest.setHubName(DEFAULT_HUB);
		exchangeStateRequest.setMethodName("QueryExchangeState");
		exchangeStateRequest.setArguments(Arrays.asList(toBittrexCurrencyString(currencyPair)));
		exchangeStateRequest.setInvocationIdentifier(1);
		webSocketSession.getBasicRemote().sendText(gson.toJson(exchangeStateRequest));
		
		// Wait for exchange state
		exchangeStateLatch.await(100, TimeUnit.SECONDS);
	}

}
