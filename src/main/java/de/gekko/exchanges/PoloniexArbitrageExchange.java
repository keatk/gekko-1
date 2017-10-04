package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.poloniex.PoloniexExchange;

public class PoloniexArbitrageExchange extends AbstractArbitrageExchange {
	public PoloniexArbitrageExchange(String apiKey, String secretKey) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(PoloniexExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));
		initServices();
	}
}
