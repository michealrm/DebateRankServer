package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class LDBallot extends Ballot<LDRound> {

	private Double a_s;
	private Double n_s;

	public Double getA_s() {
		return a_s;
	}

	public void setA_s(Double a_s) {
		this.a_s = a_s;
	}

	public Double getN_s() {
		return n_s;
	}

	public void setN_s(Double n_s) {
		this.n_s = n_s;
	}

	public LDBallot(LDRound round) {
		super(round);
	}

}
