package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.cexio.CexIOExchange;

public class CexIOArbitrageExchange extends AbstractArbitrageExchange {

	public CexIOArbitrageExchange(String apiKey, String secretKey, String userId, double takerFee) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(CexIOExchange.class.getName());
		exchangeSpecification.setUserName(userId);
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
		setMakerFee(0);
		setTakerFee(takerFee);
	}

	/**
	 * Hier können fetch-Methoden überschrieben werden, falls ein Exchange anders
	 * behandelt werden muss.
	 */

}
