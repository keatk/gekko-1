package de.gekko.websocket;

import java.math.BigDecimal;

import com.google.gson.annotations.SerializedName;

class OrderUpdate {

        @SerializedName("Type")
        protected UpdateType type;
        @SerializedName("Rate")
        protected BigDecimal rate;
        @SerializedName("Quantity")
        protected BigDecimal quantity;
        @Override
        public String toString() {
            return "OrderUpdate [type=" + type + ", rate=" + rate + ", quantity=" + quantity + "]";
        }
    }