package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.gekko.websocketNew.pojo.Hub;
import de.gekko.websocketNew.pojo.NegotiationResponse;

/**
 * Connects to Bittrex Websocket endpoint using the signalR protocol.
 * @author Maximilian Pfister
 *	TODO WORK IN PROGRESS
 */
public class BittrexWebsocket {
	
	/* constants */
	
	private static final String DEFAULT_SERVER_DOMAIN = "bittrex.com";
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:56.0) Gecko/20100101 Firefox/56.0";
	private static final String CLIENT_PROTOCOL_NUMBER = "1.5";
	private static final String DEFAULT_HUB = "corehub";

	private static final String[] USER_AGENTS = {
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
	
	public BittrexWebsocket() {
	    this.gson = new GsonBuilder()
	    	    // .setPrettyPrinting()
	    	            .create();
	    hubs = new ArrayList<>();
	}
	
	public static void main(String[] args) throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		BittrexWebsocket bittrexWebsocket = new BittrexWebsocket();
		bittrexWebsocket.init();
	}
	
	/* public methods */
	
	public void init() throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		cookieStore = new BasicCookieStore();
		httpClient = HttpClients.custom().setUserAgent(USER_AGENT).setDefaultCookieStore(cookieStore).build();
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
		
	}
	
	public void registerHub(String name) {
		Hub hub = new Hub();
		hub.setName(name);
		hubs.add(hub);
	}
	
	/* private methods */
	
	private void negotiate() throws URISyntaxException, ClientProtocolException, IOException {
		if(hubs.isEmpty()) {
			registerHub(DEFAULT_HUB);
		}
		
		// Build negotiation request
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(DEFAULT_SERVER_DOMAIN).setPath("/signalr/negotiate")
				.setParameter("clientProtocol", CLIENT_PROTOCOL_NUMBER)
				.setParameter("connectionData", gson.toJson(hubs));
		HttpGet request = new HttpGet(builder.build());

		// Send negotiation request
		HttpResponse response = httpClient.execute(request);
		
		// Read negotiation response
		String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8.name());		
		NegotiationResponse negotiationResponse = new Gson().fromJson(responseContent,  NegotiationResponse.class);
	}

}
