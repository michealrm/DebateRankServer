package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;

public class Round {
	public int id, panel;
	public Debater judge, debater, against;
	public char side;
	public boolean bye;
}
