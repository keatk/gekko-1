package de.gekko.exchanges;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bittrex.BittrexExchange;

import de.gekko.websocketNew.BittrexWebsocket;
import de.gekko.websocketNew.CloudflareScraper;

public class BittrexArbitrageExchange extends AbstractArbitrageExchange {

	public BittrexArbitrageExchange(String apiKey, String secretKey) throws URISyntaxException, ClientProtocolException, IOException, InterruptedException {
		CookieStore cookieStore = new BasicCookieStore();
		HttpClient httpClient = HttpClients.custom().setUserAgent(BittrexWebsocket.USER_AGENTS[0]).setDefaultCookieStore(cookieStore).build();
		CloudflareScraper scraper = new CloudflareScraper(httpClient);

		// Check for Cloudflare DDOS protection
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost(BittrexWebsocket.DEFAULT_SERVER_DOMAIN);
		
		HttpGet httpGet = new HttpGet(builder.build());
		HttpResponse response = httpClient.execute(httpGet);
		if (response.getStatusLine().getStatusCode() == 503) {
			// Bypass Cloudflare DDOS protection
			scraper.checkAndSolve(response, BittrexWebsocket.DEFAULT_SERVER_DOMAIN);
		} 
		
		ArrayList<String> cookies = new ArrayList<>();
		cookieStore.getCookies().forEach((item) -> {
			if(item.getName().equals("__cfduid") || item.getName().equals("cf_clearance")) {
				String cookieString = item.getName() + "=" + item.getValue();
				cookies.add(cookieString);
			}
		});
		String cookiesString = cookies.get(0) + "; " + cookies.get(1);
		
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BittrexExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchangeSpecification.setExchangeSpecificParametersItem("Cookie", cookiesString);
		exchangeSpecification.setExchangeSpecificParametersItem("User-Agent", BittrexWebsocket.USER_AGENTS[0]);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
	}
	
	/**
	 * Hier können fetch-Methoden überschrieben werden, falls ein Exchange anders
	 * behandelt werden muss.
	 */

}
