package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class Round {
	public int id, panel, affSpeaks = -1, negSpeaks = -1; // If there are multiple judges, speaks should be -1
	public Debater aff, neg;
	public ArrayList<Pair<Integer, Pair<Debater, Boolean>>> judges; // Left is ballot id, right is pair where left is judge, right is true if win
	public Boolean bye;
}
