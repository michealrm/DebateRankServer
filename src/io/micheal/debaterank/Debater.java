package io.micheal.debaterank;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import io.micheal.debaterank.util.SQLHelper;

import static io.micheal.debaterank.util.DebateHelper.insertDebater;
import static io.micheal.debaterank.util.SQLHelper.cleanString;

public class Debater {

	private String first, middle, last, surname, school, state;
	private String year; // Format: FR (Freshman), SM (Sophomore), JR (Junior), SR (Senior), Year (Grad Year)
	private Integer id;
	
	public Debater(String name, String school){
		this(name, school, null);
	}
	
	public Debater(String name, String school, String state) {
		name = name.trim().replaceAll(" \\(.+?\\)", "");
		if(state != null)
			this.state = state.trim();
		else
			this.state = null;
		if(school != null)
			this.school = school.trim();
		else
			this.school = school;
		String[] blocks = name.split(" ");
		if(blocks.length == 0)
			first = "";
		else
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
		changeInfoIfMatchesPointer();
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
		changeInfoIfMatchesPointer();
	}
	
	public void changeInfoIfMatchesPointer() {
		if(Main.pointers != null) {
			Debater replace = null;
			for(Map.Entry<Debater, Debater> entry : Main.pointers.entrySet())
				if(entry.getKey().equals(this))
					replace = entry.getValue();
			if(replace != null) {
				// Replace all info here TODO: This needs to be updated as info is added
				first = replace.getFirst();
				middle = replace.getMiddle();
				last = replace.getLast();
				surname = replace.getSurname();
				school = replace.getSchool();
				state = replace.getState();
				id = replace.getRawID();
			}
		}
	}
	
	public boolean equals(Debater debater) {
		if(id != null && debater.getRawID() != null)
			return id.intValue() == debater.getRawID().intValue();
		else
			return equalsIgnoreID(debater);
	}
	
	public boolean equalsIgnoreID(Debater debater) {
		if(debater == null)
			return false;
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
				this.id = debater.getRawID();
			return true;
		}
		return false;
	}

	public boolean equalsByLast(Debater debater) {
		String first = getFirst() == null ? debater.getFirst() : getFirst();
		Debater tempLocal = new Debater(first, getMiddle(), getLast(), getSurname(), getSchool());
		Debater tempAgainst = new Debater(first, debater.getMiddle(), debater.getLast(), debater.getSurname(), debater.getSchool());
		if(debater.getRawID() != null)
			tempAgainst.setID(debater.getRawID());
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
				this.id = debater.getRawID();
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
	
	public String getState() {
		return state;
	}
	
	public void setID(int id) {
		this.id = id;
	}

	public Integer getRawID() {
		return id;
	}

	public Integer getID(SQLHelper sql) throws SQLException {
		if(id != null)
			return id;
		ResultSet index = sql.executeQueryPreparedStatement("SELECT id, school FROM debaters WHERE first_clean<=>? AND middle_clean<=>? AND last_clean<=>? AND surname_clean<=>?", cleanString(first), cleanString(middle), cleanString(last), cleanString(surname));
		if(index.next()) {
			do {
				Debater clone = new Debater(first, middle, last, surname, index.getString(2));
				if(this.equals(clone)) {
					int ret = index.getInt(1);
					index.close();
					return ret;
				}
			} while(index.next());
		}
		index.close();
		id = insertDebater(sql, this);
		return id;
	}
	
	public String getYear() {
		return year;
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
