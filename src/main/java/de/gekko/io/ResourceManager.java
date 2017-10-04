package de.gekko.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.gekko.enums.ExchangeType;
import de.gekko.exchanges.AbstractArbitrageExchange;
import de.gekko.exchanges.BTCMarketsArbitrageExchange;
import de.gekko.exchanges.BitfinexArbitrageExchange;
import de.gekko.exchanges.BitstampArbitragerExchange;
import de.gekko.exchanges.BittrexArbitrageExchange;
import de.gekko.exchanges.CexIOArbitrageExchange;
import de.gekko.exchanges.CoinfloorArbitrageExchange;
import de.gekko.exchanges.GDaxArbitragerExchange;
import de.gekko.exchanges.KrakenArbitrageExchange;
import de.gekko.exchanges.PoloniexArbitrageExchange;
import javafx.scene.image.Image;

/**
 *
 * @author --
 * 
 *         Klasse zum Auslesen von Dateien innerhalb des Classpaths.
 *
 */
public class ResourceManager {

	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("ResourceManager");
	/**
	 * Speichert die Icons in einer Map. Wird derzeit nicht benötigt.
	 */
	private static final Map<String, Image> MAP_ICONS = new HashMap<>();
	/**
	 * Speichert den Pfad zum Configfile. Dort werden die Exchanges gespeichert.
	 */
	private static final String PATH_CONFIG_FILE = "/config.json";
	/**
	 * Speichert den Pfad zu den Icons im Classpath. Wird derzeit nicht benötigt.
	 */
	private static final String PATH_ICONS = "/icons/";
	/**
	 * Speichert den Pfad zum Resourcefile. Dort werden die Pfadverweise der Icons
	 * gespeichert. Wird derzeit nicht benötigt.
	 */
	private static final String PATH_RESOURCE_FILE = "/resources.json";

	private static Set<String> availableIcons() {
		return new HashSet<>(MAP_ICONS.keySet());
	}

	public static Image getIcon(String iconName) throws NoSuchResourceException {
		if (availableIcons().contains(iconName)) {
			return MAP_ICONS.get(iconName);
		} else {
			throw new NoSuchResourceException(iconName);
		}
	}

	/**
	 * Liest das Configfile ein.
	 * 
	 * @return Liefert eine Liste der ArbitrageExchanges.
	 */
	public static List<AbstractArbitrageExchange> loadConfigFile() {
		try {
			LOGGER.info("Loading config file from {}", PATH_CONFIG_FILE);
			final StringBuilder bldr = new StringBuilder();
			final InputStream is = ResourceManager.class.getResourceAsStream(PATH_CONFIG_FILE);
			final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			String line = null;
			while ((line = br.readLine()) != null) {
				bldr.append(line);
			}
			br.close();
			is.close();
			return parseConfigFile(bldr.toString());
		} catch (final IOException e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	/**
	 * Liest das Resourcefile ein. Wird derzeit nicht benötigt.
	 */
	public static void loadResourceFile() {
		try {
			final StringBuilder bldr = new StringBuilder();
			final InputStream is = ResourceManager.class.getResourceAsStream(PATH_CONFIG_FILE);
			final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			String line = null;
			while ((line = br.readLine()) != null) {
				bldr.append(line);
			}
			br.close();
			is.close();
			parseResourceFile(bldr.toString());
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static List<AbstractArbitrageExchange> parseConfigFile(String json) throws IOException {
		LOGGER.info("Parsing config file from {}", PATH_CONFIG_FILE);
		final JsonArray exchanges = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("exchanges");
		final List<AbstractArbitrageExchange> listExchanges = new ArrayList<>();
		for (int i = 0; i < exchanges.size(); i++) {
			final JsonObject exchange = exchanges.get(i).getAsJsonObject();

			final ExchangeType type = ExchangeType.valueOf(exchange.get("name").getAsString());
			final String apiKey = exchange.get("apikey").getAsString();
			final String secretKey = exchange.get("secretkey").getAsString();

			switch (type) {
			case BITFINEX:
				listExchanges.add(new BittrexArbitrageExchange(apiKey, secretKey));
				break;
			case BITTREX:
				listExchanges.add(new BitfinexArbitrageExchange(apiKey, secretKey));
				break;
			case GDAX:
				final String passPhrase = exchange.get("passphrase").getAsString();
				final double takerFeeGDax = exchange.get("takerfee").getAsDouble();
				listExchanges.add(new GDaxArbitragerExchange(apiKey, secretKey, passPhrase, takerFeeGDax));
				break;
			case BITSTAMP:
				final String userName = exchange.get("username").getAsString();
				listExchanges.add(new BitstampArbitragerExchange(apiKey, secretKey, userName));
				break;
			case CEXIO:
				final String userId = exchange.get("userid").getAsString();
				final double takerFeeCexIO = exchange.get("takerfee").getAsDouble();
				listExchanges.add(new CexIOArbitrageExchange(apiKey, secretKey, userId, takerFeeCexIO));
				break;
			case POLONIEX:
				listExchanges.add(new PoloniexArbitrageExchange(apiKey, secretKey));
				break;
			case KRAKEN:
				listExchanges.add(new KrakenArbitrageExchange(apiKey, secretKey));
				break;
			case BTCMARKETS:
				listExchanges.add(new BTCMarketsArbitrageExchange(apiKey, secretKey));
				break;
			case COINFLOOR:
				listExchanges.add(new CoinfloorArbitrageExchange(apiKey, secretKey));
				break;
			default:
				break;
			}
		}
		return listExchanges;
	}

	private static void parseResourceFile(String json) throws IOException {
		final JsonArray icons = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("icons");
		for (int i = 0; i < icons.size(); i++) {
			final JsonObject icon = icons.get(i).getAsJsonObject();
			final String fileName = icon.get("name").getAsString();
			final URL url = ResourceManager.class.getResource(PATH_ICONS + fileName);
			MAP_ICONS.put(fileName, new Image(url.toString()));
		}
	}

	public static String readFromPath(Path path) throws ResourceReadingException {
		try {
			final StringBuilder bldr = new StringBuilder();
			final BufferedReader reader = Files.newBufferedReader(path, Charset.defaultCharset());
			String line = null;
			while ((line = reader.readLine()) != null) {
				bldr.append(line);
			}
			reader.close();
			return bldr.toString();
		} catch (final IOException ioe) {
			throw new ResourceReadingException(ioe);
		}
	}
}
