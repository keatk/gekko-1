package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.gdax.GDAXExchange;

public class GDaxArbitragerExchange extends AbstractArbitrageExchange {

	public GDaxArbitragerExchange(String apiKey, String secretKey, String passPhrase, double takerFee) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(GDAXExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchangeSpecification.setExchangeSpecificParametersItem("passphrase", passPhrase);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
		setMakerFee(0);
		setTakerFee(takerFee);
	}

	/**
	 * Hier können fetch-Methoden überschrieben werden, falls ein Exchange anders
	 * behandelt werden muss.
	 */

	// @Override
	// public double getBalance(Currency currency) throws
	// NotAvailableFromExchangeException,
	// NotYetImplementedForExchangeException, ExchangeException, IOException {
	// return
	// accountService.getAccountInfo().getWallet().getBalance(currency).getTotal().doubleValue();
	// }

}
