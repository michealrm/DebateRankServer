package io.micheal.debaterank;

import org.apache.commons.lang3.tuple.Pair;

public class Team {

	private Integer id;
	private Pair<Debater, Debater> pair;
	
	public Team(Debater left, Debater right) {
		pair = Pair.of(left, right);
	}
	
	public void newPair (Debater left, Debater right) {
		pair = Pair.of(left, right);
	}
	
	public void setID(int id) {
		this.id = id;
	}
	
	public Integer getID() {
		return id;
		
	}
	
	public Debater getLeft() {
		return pair.getLeft();
	}
	
	public Debater getRight() {
		return pair.getRight();
	}
	
}
