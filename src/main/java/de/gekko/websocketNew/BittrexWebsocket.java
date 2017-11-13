package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;

/**
 * Connects to Bittrex Websocket endpoint using the signalR protocol.
 * @author Maximilian Pfister
 *	TODO WORK IN PROGRESS
 */
public class BittrexWebsocket {
	
	/* constants */
	
	private static final String DEFAULT_SERVER_URL = "https://www.bittrex.com";
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:56.0) Gecko/20100101 Firefox/56.0";
	
	/* variables */
	
	private CookieStore cookieStore;
	private HttpClient httpClient;
	
	public BittrexWebsocket() {

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
		HttpGet httpGet = new HttpGet(DEFAULT_SERVER_URL);
		String domain = httpGet.getURI().toString().replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)","");
		HttpResponse response = httpClient.execute(httpGet);
		if (response.getStatusLine().getStatusCode() == 503) {
			// Bypass Cloudflare DDOS protection
			scraper.checkAndSolve(response, domain);
		}
		
	}
	
	/* private methods */
	
	public void negotiate() {
		
	}

}
