package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class PFBallot extends Ballot<PFRound> {

	private Double a1_s;
	private Double a2_s;
	private Double n1_s;
	private Double n2_s;
	private Integer a1_p;
	private Integer a2_p;
	private Integer n1_p;
	private Integer n2_p;

	public PFBallot(PFRound round) {
		super(round);
	}

	public Double getA1_s() {
		return a1_s;
	}

	public void setA1_s(Double a1_s) {
		this.a1_s = a1_s;
	}

	public Double getA2_s() {
		return a2_s;
	}

	public void setA2_s(Double a2_s) {
		this.a2_s = a2_s;
	}

	public Double getN1_s() {
		return n1_s;
	}

	public void setN1_s(Double n1_s) {
		this.n1_s = n1_s;
	}

	public Double getN2_s() {
		return n2_s;
	}

	public void setN2_s(Double n2_s) {
		this.n2_s = n2_s;
	}

	public Integer getA1_p() {
		return a1_p;
	}

	public void setA1_p(Integer a1_p) {
		this.a1_p = a1_p;
	}

	public Integer getA2_p() {
		return a2_p;
	}

	public void setA2_p(Integer a2_p) {
		this.a2_p = a2_p;
	}

	public Integer getN1_p() {
		return n1_p;
	}

	public void setN1_p(Integer n1_p) {
		this.n1_p = n1_p;
	}

	public Integer getN2_p() {
		return n2_p;
	}

	public void setN2_p(Integer n2_p) {
		this.n2_p = n2_p;
	}

}
