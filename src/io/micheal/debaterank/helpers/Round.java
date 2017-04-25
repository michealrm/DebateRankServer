package io.micheal.debaterank.helpers;

public enum Round {
	
	DOUBLE_OCTOS("DO"),
	OCTOS("O"),
	QUARTERS("Q"),
	SEMIS("S"),
	FINALS("F");
	
	private String value;
	
	private Round(String value) {
		this.value = value;
	}
	
	public String toString() {
		return value;
	}
	
}
