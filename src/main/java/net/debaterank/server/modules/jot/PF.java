package net.debaterank.server.modules.jot;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.EntryInfo;

import java.util.ArrayList;

public class PF extends DuoEvent<PF, PFRound, PFBallot> {
	public PF(ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments, WorkerPool manager) {
		super(PF.class, PFRound.class, PFBallot.class, tournaments, manager);
	}
}