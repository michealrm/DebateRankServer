package net.debaterank.server.modules.tabroom;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.EntryInfo;

import java.util.ArrayList;

public class CX extends DuoEvent<net.debaterank.server.modules.jot.CX, CXRound, CXBallot> {
	public CX(ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tournaments, WorkerPool manager) {
		super(net.debaterank.server.modules.jot.CX.class, CXRound.class, CXBallot.class, tournaments, manager);
	}
}