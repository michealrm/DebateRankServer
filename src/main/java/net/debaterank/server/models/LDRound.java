package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class LDRound extends Round {

	@ManyToOne
	@JoinColumn
	private Debater a;
	@ManyToOne
	@JoinColumn
	private Debater n;

	public LDRound(Tournament tournament) {
		super(tournament);
	}

	public LDRound(Tournament tournament, Debater a, Debater n) {
		super(tournament);
		this.a = a;
		this.n = n;
	}

	public Debater getA() {
		return a;
	}

	public void setA(Debater a) {
		this.a = a;
	}

	public Debater getN() {
		return n;
	}

	public void setN(Debater n) {
		this.n = n;
	}

}
