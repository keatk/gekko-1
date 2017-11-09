package de.gekko.websocket;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

import de.gekko.websocket.BittrexWebsocket;

public class WebsocketTest {

    public static void main(String[] args) throws Exception {
        BittrexWebsocket ws = new BittrexWebsocket();
        
        ws.getOrderBook(new CurrencyPair(Currency.getInstance("BTC"), Currency.getInstance("USDT")));
        ws.getOrderBook(new CurrencyPair(Currency.getInstance("ETH"), Currency.getInstance("USDT")));
        ws.getOrderBook(new CurrencyPair(Currency.getInstance("ETH"), Currency.getInstance("BTC")));
        
        while(true) {
        		Thread.sleep(1000);
        }
    }
}
