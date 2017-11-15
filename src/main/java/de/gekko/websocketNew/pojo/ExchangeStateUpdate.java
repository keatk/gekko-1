package de.gekko.websocketNew.pojo;

import java.util.Arrays;

import com.google.gson.annotations.SerializedName;

import de.gekko.websocketNew.ChannelHandlerUpdate;

public class ExchangeStateUpdate implements ChannelHandlerUpdate{
	
    @SerializedName("MarketName")
    private String marketName;      // always null
    
    @SerializedName("Nounce")
    private long nounce;
    
    @SerializedName("Buys")
    private Order[] buys;
    
    @SerializedName("Sells")
    private Order[] sells;
    
    @SerializedName("Fills")
    private Fill[] fills;
    
    @Override
    public String toString() {
        return "QueryExchangeState [marketName=" + marketName + ", nounce=" + nounce + ", buys=" + Arrays.toString(buys)
                + ", sells=" + Arrays.toString(sells) + ", fills=" + Arrays.toString(fills) + "]";
    }

	public String getMarketName() {
		return marketName;
	}

	public long getNounce() {
		return nounce;
	}

	public Order[] getBuys() {
		return buys;
	}

	public Order[] getSells() {
		return sells;
	}

	public Fill[] getFills() {
		return fills;
	}
 
}