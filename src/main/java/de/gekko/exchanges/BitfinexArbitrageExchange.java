package de.gekko.exchanges;

import java.io.IOException;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import de.gekko.enums.ExchangeType;

public class BitfinexArbitrageExchange extends AbstractArbitrageExchange {

	public BitfinexArbitrageExchange(ExchangeType type, String apiKey, String secretKey, CurrencyPair currencyPair) {
		super(type, apiKey, secretKey, currencyPair);
	}

	@Override
	public double checkBalance(Currency currency) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		return  accountService.getAccountInfo().getWallet().getBalance(currency).getTotal().doubleValue();
	}

}
