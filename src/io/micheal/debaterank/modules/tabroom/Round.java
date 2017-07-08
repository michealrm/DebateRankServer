package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class Round {
	public int id, panel, affSpeaks, negSpeaks; // If there are multiple jduges, speaks should be -1
	public Debater aff, neg;
	public ArrayList<Pair<Debater, Boolean>> judges; // Left is judge, right is win if true
	public boolean bye;
}
