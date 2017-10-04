package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinfloor.CoinfloorExchange;

public class CoinfloorArbitrageExchange extends AbstractArbitrageExchange {
	public CoinfloorArbitrageExchange(String apiKey, String secretKey) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(CoinfloorExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
	}

}
