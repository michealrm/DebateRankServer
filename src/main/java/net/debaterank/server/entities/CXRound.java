package net.debaterank.server.entities;

import javax.persistence.*;

@Entity
@Table
public class CXRound {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	@OneToOne
	@JoinColumn(nullable = false)
	private Tournament tournament;
	@ManyToOne
	@JoinColumn
	private Team a;
	@ManyToOne
	@JoinColumn
	private Team n;
	private boolean bye;
	private String round;
	private String absUrl;

	public CXRound() {}

	public CXRound(Tournament tournament) {
		this.tournament = tournament;
	}

	public CXRound(Tournament tournament, Team a, Team n) {
		this.tournament = tournament;
		this.a = a;
		this.n = n;
	}

	public boolean isBye() {
		return bye;
	}

	public void setBye(boolean bye) {
		this.bye = bye;
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
}
