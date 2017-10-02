package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.v1.BitfinexExchange;

public class BitfinexArbitrageExchange extends AbstractArbitrageExchange {

	public BitfinexArbitrageExchange(String apiKey, String secretKey) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BitfinexExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
	}

	// @Override
	// public double getBalance(Currency currency) throws
	// NotAvailableFromExchangeException,
	// NotYetImplementedForExchangeException, ExchangeException, IOException {
	// return
	// accountService.getAccountInfo().getWallet().getBalance(currency).getTotal().doubleValue();
	// }

}
