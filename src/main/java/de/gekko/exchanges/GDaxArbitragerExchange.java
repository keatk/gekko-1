package de.gekko.exchanges;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.gdax.GDAXExchange;

public class GDaxArbitragerExchange extends AbstractArbitrageExchange {

	public GDaxArbitragerExchange(String apiKey, String secretKey, String passphrase) {
		super(GDAXExchange.class, apiKey, secretKey);
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(GDAXExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchangeSpecification.setPassword(passphrase);
		createExchange(exchangeSpecification);
	}

}
