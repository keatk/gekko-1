package de.gekko.exchanges;

import java.io.IOException;

import org.knowm.xchange.bitfinex.v1.BitfinexExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

public class BitfinexArbitrageExchange extends AbstractArbitrageExchange {

	public BitfinexArbitrageExchange(String apiKey, String secretKey) {
		super(BitfinexExchange.class, apiKey, secretKey);
	}

	@Override
	public double checkBalance(Currency currency) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return accountService.getAccountInfo().getWallet().getBalance(currency).getTotal().doubleValue();
	}

}
