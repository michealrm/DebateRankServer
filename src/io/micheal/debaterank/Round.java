package io.micheal.debaterank;

public class Round {

	private String against;
	private boolean win;
	private double speaks;
	
	public Round(String against, boolean win, double speaks) {
		this.against = against;
		this.win = win;
		this.speaks = speaks;
	}
	
	public String getOpponent() {
		return against;
	}
	
	public boolean getWin() {
		return win;
	}
	
	public double getSpeaks() {
		return speaks;
	}
	
}
