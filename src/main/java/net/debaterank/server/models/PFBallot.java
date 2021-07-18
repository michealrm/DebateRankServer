package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class PFBallot extends DuoBallot<PFRound> {

	public PFBallot() {}
	public PFBallot(PFRound round) {
		super(round);
	}

}