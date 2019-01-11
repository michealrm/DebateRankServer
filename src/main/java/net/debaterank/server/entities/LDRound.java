package net.debaterank.server.entities;

import javax.persistence.*;

@Entity
@Table
public class LDRound {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	@OneToOne
	@JoinColumn(nullable = false)
	private Tournament tournament;
	@OneToOne
	@JoinColumn
	private Debater a;
	@OneToOne
	@JoinColumn
	private Debater n;
	private boolean bye;
	private String round;
	private String absUrl;

	public LDRound(Tournament tournament) {
		this.tournament = tournament;
	}

	public LDRound(Tournament tournament, Debater a, Debater n) {
		this.tournament = tournament;
		this.a = a;
		this.n = n;
	}

	public LDRound() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
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

	public String getRound() {
		return round;
	}

	public void setRound(String round) {
		this.round = round;
	}

	public String getAbsUrl() {
		return absUrl;
	}

	public void setAbsUrl(String absUrl) {
		this.absUrl = absUrl;
	}

	public boolean isBye() {
		return bye;
	}

	public void setBye(boolean bye) {
		this.bye = bye;
	}

}