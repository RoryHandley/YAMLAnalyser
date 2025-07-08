package SIMPLAnalyser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Product {
	private String name;
	private String type;
	private Map<String, String> secretMap;
	private double version;
	
	// Constructor with Args
	public Product(String type) {
		this.setType(type);
		// Create an empty secret map for each product
		secretMap = new HashMap<String, String>();
	}
	
	// Getters/Setters
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Map<String, String> getSecretMap() {
		return secretMap;
	}
	
	public void addToSecretMap(String location, String mop) {
		this.secretMap.put(location, mop);
	}
}
