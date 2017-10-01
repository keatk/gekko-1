package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bittrex.v1.BittrexExchange;

public class BittrexArbitrageExchange extends AbstractArbitrageExchange {

	public BittrexArbitrageExchange(String apiKey, String secretKey) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BittrexExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);

		initServices();
	}

}
