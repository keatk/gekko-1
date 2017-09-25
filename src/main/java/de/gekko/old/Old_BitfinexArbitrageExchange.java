package de.gekko.old;

import java.io.IOException;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.v1.BitfinexExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

public class Old_BitfinexArbitrageExchange extends Old_AbstractArbitrageExchange{
	
	public Old_BitfinexArbitrageExchange(CurrencyPair currencyPair, String apiKey, String secretKey){
		super(BitfinexExchange.class, "Bitfinex",  currencyPair, apiKey, secretKey);
	}
	
	@Override
	public void checkBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
	    baseAmount = accountService.getAccountInfo().getWallet().getBalance(currencyPair.base).getTotal().doubleValue();
	    counterAmount = accountService.getAccountInfo().getWallet().getBalance(currencyPair.counter).getTotal().doubleValue();
	}

}
