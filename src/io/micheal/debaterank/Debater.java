package io.micheal.debaterank;

import java.util.ArrayList;
import java.util.Arrays;

import io.micheal.debaterank.util.SQLHelper;

public class Debater {

	private String first, middle, last, surname, school;
	private Integer id;
	
	public Debater(String name, String school) throws UnsupportedNameException {
		name = name.replaceAll(" \\(.+?\\)", "");
		if(school != null)
			this.school = school.trim();
		else
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
		if(school != null)
			this.school = school.trim();
		else
			this.school = school;
	}
	
	public boolean equals(Debater debater) {
		boolean replaceThis = false;
		String first = debater.getFirst();
		String last = debater.getLast();
		String school = debater.getSchool();
		if(school != null && this.school != null) {
			
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
		if(((first == null && this.first == null) || (this.first != null && SQLHelper.cleanString(this.first).equals(SQLHelper.cleanString(first)))) &&
				(((this.last == null || last == null) || (this.last != null && SQLHelper.cleanString(this.last).equals(SQLHelper.cleanString(last)))))) {
			if(this.first == null)
				this.first = debater.getFirst();
			if(this.middle == null)
				this.middle = debater.getMiddle();
			if(this.last == null)
				this.last = debater.getLast();
			if(this.surname == null)
				this.surname = debater.getSurname();
			if(replaceThis)
				this.school = debater.getSchool();
			if(this.id == null)
				this.id = debater.getID();
			return true;
		}
		return false;
	}

	public boolean equalsByLast(Debater debater) {
		String first = getFirst() == null ? debater.getFirst() : getFirst();
		Debater tempLocal = new Debater(first, getMiddle(), getLast(), getSurname(), getSchool());
		Debater tempAgainst = new Debater(first, debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
		if(debater.getID() != null)
			tempAgainst.setID(debater.getID());
		if(tempLocal.equals(tempAgainst)) {
			if(this.first == null)
				this.first = debater.getFirst();
			if(this.middle == null)
				this.middle = debater.getMiddle();
			if(this.last == null)
				this.last = debater.getLast();
			if(this.surname == null)
				this.surname = debater.getSurname();
			if(this.school == null)
				this.school = debater.getSchool();
			if(this.id == null)
				this.id = debater.getID();
			return true;
		}
		else
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
	
	public String toString() {
		String str = "";
		str += first;
		if(middle != null)
			str += " " + middle;
		if(last != null)
			str += " " + last;
		if(surname != null)
			str += " " + surname;
		if(school != null)
			str += " (" + school + ")";
		return str;
	}
	
}
