package io.micheal.debatescout;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Debater {

	private String first, middle, last, surname, school;
	private Integer id;
	
	public Debater(String name, String school) throws UnsupportedNameException {
		this.school = school;
		String[] blocks = name.split(" ");
		if(blocks.length == 0)
			throw new UnsupportedNameException(name);
		first = blocks[0];
		if(blocks.length == 2)
			last = blocks[1];
		else if(blocks.length == 3) {
			if(blocks[2].endsWith(".") || blocks[2].length() == 2 || blocks[2].matches("III|IV|V|VI|VII|VIII|IX|X")) {
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
		String first = debater.getFirst();
		String last = debater.getLast();
		String school = debater.getSchool();
		String regex = "";
		String[] blocks = this.school.split(" ");
		if(school != null && this.school != null) {
			if(blocks.length <= 1)
				regex = blocks[0];
			else {
				for(String s : blocks)
					regex += s + "|";
				regex = regex.substring(0, regex.length()-1);
			}
		}
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(school);
		if((this.first == null && first == null) || (this.first != null && first != null && this.first.equalsIgnoreCase(first)) &&
				(this.last == null && last == null) || (this.last != null && last != null && this.last.equalsIgnoreCase(last)) &&
				(school == null || this.school == null || (school != null && this.school != null && m.find())))
			return true;
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
