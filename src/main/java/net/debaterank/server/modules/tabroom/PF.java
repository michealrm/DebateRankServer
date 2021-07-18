package net.debaterank.server.modules.tabroom;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.EntryInfo;

import java.util.ArrayList;

public class PF extends DuoEvent<net.debaterank.server.modules.jot.PF, PFRound, PFBallot> {
	public PF(ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tournaments, WorkerPool manager) {
		super(net.debaterank.server.modules.jot.PF.class, PFRound.class, PFBallot.class, tournaments, manager);
	}
}