package de.gekko.exchanges;

import java.io.IOException;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.gdax.GDAXExchange;

public class GDaxArbitragerExchange extends AbstractArbitrageExchange {

	public GDaxArbitragerExchange(String apiKey, String secretKey, String passPhrase) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(GDAXExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchangeSpecification.setExchangeSpecificParametersItem("passphrase", passPhrase);
		exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);

		initServices();
	}

	@Override
	public double getBalance(Currency currency) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return accountService.getAccountInfo().getWallet().getBalance(currency).getTotal().doubleValue();
	}

}
