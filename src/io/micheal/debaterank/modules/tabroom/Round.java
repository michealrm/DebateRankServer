package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;

import java.util.ArrayList;

public class Round {
	public RoundInfo roundInfo;
	public Debater aff, neg;
	public ArrayList<JudgeBallot> judges; // Left is ballot id, right is pair where left is judge, right is Pair where left is speaks and right is true if aff win
	public Boolean bye;
}
