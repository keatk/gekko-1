package de.gekko.exchanges;

import org.knowm.xchange.currency.CurrencyPair;

import de.gekko.enums.ExchangeType;

public class BittrexArbitrageExchange extends AbstractArbitrageExchange {
	
	public BittrexArbitrageExchange(ExchangeType type, String apiKey, String secretKey, CurrencyPair currencyPair) {
		super(type, apiKey, secretKey, currencyPair);
	}

}
