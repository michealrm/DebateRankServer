package net.debaterank.server.modules.jot;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.EntryInfo;

import java.util.ArrayList;

public class CX extends DuoEvent<CX, CXRound, CXBallot> {
	public CX(ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments, WorkerPool manager) {
		super(CX.class, CXRound.class, CXBallot.class, tournaments, manager);
	}
}