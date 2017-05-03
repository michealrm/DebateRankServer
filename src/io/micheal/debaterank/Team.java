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

	public boolean equals(Team team) {
		return ((pair.getLeft().equals(team.getLeft()) && pair.getRight().equals(pair.getRight())) ||
				(pair.getLeft().equals(team.getRight()) && pair.getRight().equals(pair.getLeft())));
	}

	public boolean equalsByLast(Team team) {
		return (pair.getLeft().equals(team.getLeft()) && pair.getRight().equals(team.getRight())) || (pair.getLeft().equals(team.getRight()) && pair.getRight().equals(team.getLeft()));
	}

}
