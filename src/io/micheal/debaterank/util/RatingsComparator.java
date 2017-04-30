package io.micheal.debaterank.util;

import java.util.Comparator;

import org.goochjs.glicko2.Rating;

public class RatingsComparator implements Comparator<Rating> {

	@Override
	public int compare(Rating o1, Rating o2) { // Descending
		if(o1.getRating() < o2.getRating()) return 1;
		if(o1.getRating() > o2.getRating()) return -1;
		return 0;
	}
	
}
