package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitstamp.BitstampExchange;

public class BitstampArbitragerExchange extends AbstractArbitrageExchange {

	public BitstampArbitragerExchange(String apiKey, String secretKey, String userName) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BitstampExchange.class.getName());
		exchangeSpecification.setUserName(userName);
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
	}
	
	/**
	 * Hier können fetch-Methoden überschrieben werden, falls ein Exchange anders
	 * behandelt werden muss.
	 */

}
