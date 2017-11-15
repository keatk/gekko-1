package de.gekko.websocketNew.pojo;

import java.util.Arrays;

import com.google.gson.annotations.SerializedName;

import de.gekko.websocketNew.Handable;

public class ExchangeState implements Handable{
	
    @SerializedName("MarketName")
    protected String marketName;      // always null
    
    @SerializedName("Nounce")
    protected long nounce;
    
    @SerializedName("Buys")
    protected Order[] buys;
    
    @SerializedName("Sells")
    protected Order[] sells;
    
    @SerializedName("Fills")
    protected Fill[] fills;
    
    @Override
    public String toString() {
        return "QueryExchangeState [marketName=" + marketName + ", nounce=" + nounce + ", buys=" + Arrays.toString(buys)
                + ", sells=" + Arrays.toString(sells) + ", fills=" + Arrays.toString(fills) + "]";
    }
    
}