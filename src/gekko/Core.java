package gekko;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import gekko.arbitrager.Arbitrager;
import gekko.exchanges.BitfinexArbitrageExchange;
import gekko.exchanges.BittrexArbitrageExchange;


public class Core {

	public static void main(String[] args) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {
		
		CurrencyPair currencyPair = new CurrencyPair(Currency.LTC, Currency.BTC);
		
		String apiKey1 = "";
		String secretKey1 = "";
		
		String apiKey2 = "";
		String secretKey2 = "";
		
		BittrexArbitrageExchange bittrexExchange = new BittrexArbitrageExchange(currencyPair, "apiKey1", "secretKey1");
		BitfinexArbitrageExchange bitfinexExchange = new BitfinexArbitrageExchange(currencyPair, "apiKey2", "secretKey2");

		Arbitrager arb1 = new Arbitrager(bittrexExchange, bitfinexExchange, currencyPair);
		
		long startTime = System.nanoTime();
		
		while (true) {

			try {
				arb1.updateMarketData();
				arb1.arbTest1();
				arb1.arbTest2();
				
				Thread.sleep(3000);

			} catch (ConnectException | SocketTimeoutException | UnknownHostException
					| si.mazi.rescu.HttpStatusIOException Exception) {
				System.err.println("Connection Failed. Retry in 30 sec...");
				Thread.sleep(30000);
			}
		}
	    	


	}

}
