package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.Judge;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class JudgeBallot {
	public ArrayList<Integer> ballots;
	public Judge judge;
	public Debater winner;
	public double affSpeaks, negSpeaks;
}
