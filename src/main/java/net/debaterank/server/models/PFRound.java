package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class PFRound extends DuoRound {

	public PFRound() {}
	public PFRound(Tournament tournament) {
		super(tournament);
	}
	public PFRound(Tournament tournament, Team a, Team n) {
		super(tournament, a, n);
	}

}
