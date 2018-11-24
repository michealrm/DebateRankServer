package net.debaterank.server.entities;

import javax.persistence.*;

@Entity
@Table
public class LDRound {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private Tournament tournament;
	@OneToOne
	private Debater a;
	@OneToOne
	private Debater n;

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
}
