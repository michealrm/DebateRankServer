package io.micheal.debatescout;

public class Test {

	public static void main(String[] args) throws UnsupportedNameException {
		Debater one = new Debater("Jake Richter", "Royse City HS");
		Debater two = new Debater("Jake Richter", "Royse City HS");
		System.out.println(one.equals(two));
	}
	
}
