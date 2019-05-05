package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class PFRound extends Round {

	@ManyToOne
	@JoinColumn
	private Team a;
	@ManyToOne
	@JoinColumn
	private Team n;

	public PFRound(Tournament tournament) {
		super(tournament);
	}

	public PFRound(Tournament tournament, Team a, Team n) {
		super(tournament);
		this.a = a;
		this.n = n;
	}

	public Team getA() {
		return a;
	}

	public void setA(Team a) {
		this.a = a;
	}

	public Team getN() {
		return n;
	}

	public void setN(Team n) {
		this.n = n;
	}
}
