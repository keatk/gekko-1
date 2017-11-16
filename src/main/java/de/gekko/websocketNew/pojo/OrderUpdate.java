package de.gekko.websocketNew.pojo;

import java.math.BigDecimal;

import com.google.gson.annotations.SerializedName;

import de.gekko.websocket.UpdateType;

public class OrderUpdate {
	
    @SerializedName("Quantity")
    private BigDecimal quantity;
    
    @SerializedName("Rate")
    private BigDecimal rate;
    
    @SerializedName("Type")
    protected UpdateType type;
    
    @Override
    public String toString() {
        return "Order [quantity=" + quantity + ", rate=" + rate + "]";
    }

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public UpdateType getType() {
		return type;
	}
	
	
}