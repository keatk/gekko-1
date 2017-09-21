package gekko.exchanges;

import org.knowm.xchange.bittrex.v1.BittrexExchange;
import org.knowm.xchange.currency.CurrencyPair;

public class BittrexArbitrageExchange extends AbstractArbitrageExchange {
	
	public BittrexArbitrageExchange(CurrencyPair currencyPair, String apiKey, String secretKey){
		super(BittrexExchange.class, "BitTrex",  currencyPair, apiKey, secretKey);
	}

}
