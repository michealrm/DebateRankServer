package net.debaterank.server.entities;

import javax.persistence.*;

@Entity
@Table
public class PFRound {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private Tournament tournament;
	private Debater a1;
	private Debater a2;
	private Debater n1;
	private Debater n2;

	public PFRound(Tournament tournament, Debater a1, Debater a2, Debater n1, Debater n2) {
		this.tournament = tournament;
		this.a1 = a1;
		this.a2 = a2;
		this.n1 = n1;
		this.n2 = n2;
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

	public Debater getA1() {
		return a1;
	}

	public void setA1(Debater a1) {
		this.a1 = a1;
	}

	public Debater getA2() {
		return a2;
	}

	public void setA2(Debater a2) {
		this.a2 = a2;
	}

	public Debater getN1() {
		return n1;
	}

	public void setN1(Debater n1) {
		this.n1 = n1;
	}

	public Debater getN2() {
		return n2;
	}

	public void setN2(Debater n2) {
		this.n2 = n2;
	}

}
