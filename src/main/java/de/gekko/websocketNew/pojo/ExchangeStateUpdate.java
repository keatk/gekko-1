package de.gekko.websocketNew.pojo;

import java.util.Arrays;
import java.util.List;

import com.google.gson.annotations.SerializedName;


public class ExchangeStateUpdate {
	
    @SerializedName("MarketName")
    private String marketName;      // always null
    
    @SerializedName("Nounce")
    private long nounce;
    
    @SerializedName("Buys")
    private List<OrderUpdate> buys;
    
    @SerializedName("Sells")
    private List<OrderUpdate> sells;
    
//    @SerializedName("Fills")
//    private Fill[] fills;
    
//    @Override
//    public String toString() {
//        return "QueryExchangeState [marketName=" + marketName + ", nounce=" + nounce + ", buys=" + Arrays.toString(buys)
//                + ", sells=" + Arrays.toString(sells) + ", fills=" + Arrays.toString(fills) + "]";
//    }

	public String getMarketName() {
		return marketName;
	}

	public long getNounce() {
		return nounce;
	}

	public List<OrderUpdate> getBuys() {
		return buys;
	}

	public List<OrderUpdate> getSells() {
		return sells;
	}

//	public Fill[] getFills() {
//		return fills;
//	}
 
}