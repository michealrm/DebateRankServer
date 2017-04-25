package io.micheal.debaterank;

import java.util.ArrayList;
import java.util.Arrays;

import io.micheal.debaterank.helpers.SQLHelper;

public class Debater {

	private String first, middle, last, surname, school;
	private Integer id;
	
	public Debater(String name, String school) throws UnsupportedNameException {
		name = name.replaceAll(" \\(.+?\\)", "");
		this.school = school;
		String[] blocks = name.split(" ");
		if(blocks.length == 0)
			throw new UnsupportedNameException(name);
		first = blocks[0];
		if(blocks.length == 2)
			last = blocks[1];
		else if(blocks.length == 3) {
			if(blocks[2].endsWith(".") || blocks[2].length() == 2 || blocks[2].matches("III|IV|V|VI|VII|VIII|IX|X|Junior")) {
				last = blocks[1];
				surname = blocks[2];
			}
			else {
				middle = blocks[1];
				last = blocks[2];
			}
		}
		else if(blocks.length >= 4) {
			middle = blocks[1];
			last = blocks[2];
			surname = blocks[3];
		}
	}

	public Debater(String first, String middle, String last, String surname, String school) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.surname = surname;
		this.school = school;
	}
	
	public boolean equals(Debater debater) {
		boolean replaceThis = false;
		String first = debater.getFirst();
		String last = debater.getLast();
		String school = debater.getSchool();
		if(school != null && this.school != null) { // TODO: Make this not look terrible
			
			ArrayList<String> compare = new ArrayList<String>(Arrays.asList(SQLHelper.cleanString(this.school).split(" ")));
			ArrayList<String> against = new ArrayList<String>(Arrays.asList(SQLHelper.cleanString(school).split(" ")));
			if(SQLHelper.cleanString(this.school).split(" ").length < SQLHelper.cleanString(school).split(" ").length) {
				compare = new ArrayList<String>(Arrays.asList(SQLHelper.cleanString(school).split(" ")));
				against = new ArrayList<String>(Arrays.asList(SQLHelper.cleanString(this.school).split(" ")));
			}
			
			if(!compare.containsAll(against))
				return false;
			else
				replaceThis = true;
			
		}
		else if(this.school == null && school != null)
			replaceThis = true;
		
		if((SQLHelper.cleanString(this.first).equals(SQLHelper.cleanString(first))) &&
				((this.last == null || last == null) || SQLHelper.cleanString(this.last).equals(SQLHelper.cleanString(last)))) {
			if(replaceThis) {
				this.first = debater.getFirst();
				this.middle = debater.getMiddle();
				this.last = debater.getLast();
				this.surname = debater.getSurname();
				this.school = debater.getSchool();
			}
			return true;
		}
		return false;
	}
	
	public String getFirst() {
		return first;
	}

	public String getMiddle() {
		return middle;
	}

	public String getLast() {
		return last;
	}

	public String getSurname() {
		return surname;
	}

	public String getSchool() {
		return school;
	}
	
	public void setID(int id) {
		this.id = id;
	}
	
	public Integer getID() {
		return id;
	}
	
}
