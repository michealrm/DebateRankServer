package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class LDBallot {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn
	private LDRound round;
	private Double a_s;
	private Double n_s;
	@ManyToOne
	@JoinColumn
	private Judge judge;
	private String decision;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public LDRound getRound() {
		return round;
	}

	public void setRound(LDRound round) {
		this.round = round;
	}

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

	public String getDecision() {
		return decision;
	}

	public void setDecision(String decision) {
		this.decision = decision;
	}

	public Judge getJudge() {
		return judge;
	}

	public void setJudge(Judge judge) {
		this.judge = judge;
	}

	public LDBallot(LDRound round) {
		this.round = round;
	}

	public LDBallot() {}

}