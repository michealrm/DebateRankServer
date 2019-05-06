package net.debaterank.server.models;

import javax.persistence.*;

@Entity
@Table
public class CXRound extends DuoRound {

	public CXRound() {}
	public CXRound(Tournament tournament) {
		super(tournament);
	}
	public CXRound(Tournament tournament, Team a, Team n) {
		super(tournament, a, n);
	}

}