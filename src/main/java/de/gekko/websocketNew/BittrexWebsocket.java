package de.gekko.websocketNew;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.websocketNew.pojo.ExchangeStateUpdate;

/**
 * Connects to Bittrex Websocket endpoint using the signalR protocol.
 * 
 * @author Maximilian Pfister TODO WORK IN PROGRESS
 */
public class BittrexWebsocket {

	/* constants */

	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocket.class);
	
	public static final String DEFAULT_SERVER_DOMAIN = "bittrex.com";
	public static final String CLIENT_PROTOCOL_NUMBER = "1.5";
	public static final String DEFAULT_HUB = "corehub";	

	public static final String[] USER_AGENTS = {
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:56.0) Gecko/20100101 Firefox/56.0",
			"Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:46.0) Gecko/20100101 Firefox/46.0",
			"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:41.0) Gecko/20100101 Firefox/41.0" };

	/* variables */
	
	private static BittrexWebsocket instance;

	private CookieStore cookieStore;
	private HttpClient httpClient;
	private BittrexWebsocketHttp bittrexWebsocketHttp;
	private Map<CurrencyPair, ChannelHandler> channelHandlers;
	
	/* constructors */

	private BittrexWebsocket() {
		channelHandlers = new HashMap<>();
	}

	/* public methods */
	
	//TODO remove after class is finished
	public static void main(String[] args)
			throws ClientProtocolException, IOException, URISyntaxException, InterruptedException {
		BittrexWebsocket bittrexWebsocket = BittrexWebsocket.getInstance();
		bittrexWebsocket.init();
		bittrexWebsocket.subscribeOrderbook(new CurrencyPair(Currency.getInstance("USDT"), Currency.getInstance("BTC")));
//		bittrexWebsocket.subscribeOrderbook(new CurrencyPair(Currency.getInstance("ETH"), Currency.getInstance("XRP")));
	}

	/**
	 * Returns singleton instance.
	 * @return
	 */
	public static synchronized BittrexWebsocket getInstance() {
		if (BittrexWebsocket.instance == null) {
			BittrexWebsocket.instance = new BittrexWebsocket();
			try {
				BittrexWebsocket.instance.init();
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return BittrexWebsocket.instance;
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
		
		bittrexWebsocketHttp = new BittrexWebsocketHttp(httpClient, cookieStore);

	}
	
	/**
	 * Subscribe to orderbook related to currencyPair.
	 * @param currencyPair
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException 
	 */
	public synchronized void subscribeOrderbook(CurrencyPair currencyPair) throws IOException, InterruptedException, URISyntaxException {
		bittrexWebsocketHttp.subscribeToOrderbook(toBittrexCurrencyString(currencyPair));
	}
	
	public synchronized void registerSubscriber(CurrencyPair currencyPair, UpdateableOrderbook updateableObject) throws Exception {
		if(!channelHandlers.containsKey(currencyPair)) {
			createChannelHandler(currencyPair);
		}
		channelHandlers.get(currencyPair).addSubscriber(updateableObject);
		subscribeOrderbook(currencyPair);
	}
	
	/**
	 * Sends update to channelHandler.
	 * @param currencyPair
	 * @param update
	 */
	public void sendToChannelHandler(String currencyPairBittrex, ExchangeStateUpdate update) {
		CurrencyPair currencyPair = toCurrencyPair(currencyPairBittrex);
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
		LOGGER.info("CREATE CHANNEL HANDLER " + currencyPair);
		if(!channelHandlers.containsKey(currencyPair)) {
			channelHandlers.put(currencyPair, ChannelHandler.createInstance(currencyPair));
		}
	}
	
	

}
