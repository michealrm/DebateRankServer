package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class JudgeBallot {
	public ArrayList<Integer> ballots;
	public Debater judge, winner;
	public double affSpeaks, negSpeaks;
}
