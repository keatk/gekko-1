package de.gekko.websocketNew.pojo;

import com.google.gson.annotations.SerializedName;

public class Hub {
	
	@SerializedName("Name")
	private String name;

	public void setName(String name) {
		this.name = name;
	}

}
