package net.debaterank.server.entities;

import net.debaterank.server.entities.Debater;
import net.debaterank.server.entities.LDRound;

import javax.persistence.*;

@Entity
@Table
public class LDBallot {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	@Column(nullable = false)
	@OneToOne
	private LDRound round;
	@OneToOne
	private Debater a;
	@OneToOne
	private Debater n;
	private Double a_s;
	private Double n_s;
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

	public LDBallot(LDRound round, Debater a, Debater n, Double a_s, Double n_s, String decision) {
		this.round = round;
		this.a = a;
		this.n = n;
		this.a_s = a_s;
		this.n_s = n_s;
		this.decision = decision;
	}

	public LDBallot() {
	}

	public LDBallot(LDRound round, Debater a, Debater n, String decision) {
		this.round = round;
		this.a = a;
		this.n = n;
		this.decision = decision;
	}
}
