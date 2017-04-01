package io.micheal.debatescout;

public class Tournament {

	private String name, link, state, date;
	
	public Tournament(String name, String link, String state, String date) {
		this.name = name;
		this.link = link;
		this.state = state;
		if(date.matches("\\d\\/\\d\\d\\/\\d\\d\\d\\d"))
			date = "0" + date;
		this.date = date;
	}
	
	public String getName() {
		return name;
	}
	
	public String getLink() {
		return link;
	}
	
	public String getState() {
		return state;
	}
	
	public String getDate() {
		return date;
	}
	
}
