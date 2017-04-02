package io.micheal.debatescout;

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
		if(school != null && this.school != null) {
			String[] blocks1 = SQLHelper.cleanString(this.school).split(" ");
			String[] blocks2 = SQLHelper.cleanString(school).split(" ");
			if(blocks1.length > blocks2.length) {
				String[] temp = blocks2;
				blocks2 = blocks1;
				blocks1 = temp;
			}
			boolean found = false;
			for(int i = 0;i<blocks2.length;i++) {
				for(int k = 0;k<blocks1.length;k++)
					if(blocks2[i].equals(blocks1[k]))
						found = true;
				if(!found)
					return false;
				found = false;
			}
		}
		if(((this.first == null && first == null) || SQLHelper.cleanString(this.first).equals(SQLHelper.cleanString(first))) &&
				((this.last == null && last == null) || SQLHelper.cleanString(this.last).equals(SQLHelper.cleanString(last)))) {
			return true;
		}

		System.out.println(first + " " + last + " from " + school + " didnt match " + this.first + " " + this.last + " from " + this.school);
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
