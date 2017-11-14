package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.gekko.websocketNew.pojo.Hub;
import de.gekko.websocketNew.pojo.NegotiationResponse;

/**
 * Connects to Bittrex Websocket endpoint using the signalR protocol.
 * 
 * @author Maximilian Pfister TODO WORK IN PROGRESS
 */
public class BittrexWebsocket {

	/* constants */

	final static CountDownLatch messageLatch = new CountDownLatch(1);

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

	/* variables */

	private CookieStore cookieStore;
	private HttpClient httpClient;
	private Gson gson;

	private ArrayList<Hub> hubs;

	NegotiationResponse negotiationResponse;

	public BittrexWebsocket() {
		this.gson = new GsonBuilder()
				// .setPrettyPrinting()
				.create();
		hubs = new ArrayList<>();
	}

	public static void main(String[] args)
			throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		BittrexWebsocket bittrexWebsocket = new BittrexWebsocket();
		bittrexWebsocket.init();
	}

	/* public methods */

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

	public void registerHub(String name) {
		Hub hub = new Hub();
		hub.setName(name);
		hubs.add(hub);
	}

	/* private methods */

	private void negotiate() throws URISyntaxException, ClientProtocolException, IOException {
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
		System.out.println(responseContent);
		negotiationResponse = new Gson().fromJson(responseContent, NegotiationResponse.class);
		LOGGER.info("Negotiation response received.");
	}

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

		System.out.println(builder.build().toString());

		LOGGER.info("Starting websocket transport...");
		try {
			// Connect websocket to server
			WebSocketContainer container = ContainerProvider.getWebSocketContainer();
			container.connectToServer(BittrexWebsocketClientEndpoint.class, cec, builder.build());
			messageLatch.await(100, TimeUnit.SECONDS);
		} catch (DeploymentException | InterruptedException | IOException ex) {
			// Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
		}
		LOGGER.info("Websocket transport started.");
	}
	
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
		if(gson.fromJson(responseContent, JsonObject.class).get("Response").toString().equals("started")) {
			LOGGER.info("Start notification successful");
		} else {
			LOGGER.info("ERROR: Start notification failed.");
		}

	}

}
