package net.debaterank.server.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.debaterank.server.Server;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;

@Entity("judges")
public class Judge {

	public static String cleanString(String str) {
		return str.toLowerCase();
	}

	public Judge() {}

	public Judge(ObjectId id, String first, String middle, String last, String surname, School school) {
		this.id = id;
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.surname = surname;
		this.school = school;
		changeInfoIfMatchesPointer();
	}

	public Judge(String first, String middle, String last, String surname, School school) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.surname = surname;
		this.school = school;
		changeInfoIfMatchesPointer();
	}

	public Judge(String name, School school) {
		name = name.trim().replaceAll(" \\(.+?\\)", "");
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

	public void changeInfoIfMatchesPointer() {
		if(Server.judgePointers != null) {
			for(JudgePointer pointer : Server.judgePointers) {
				if (pointer.getFirst().equalsIgnoreCase(first) && pointer.getMiddle().equalsIgnoreCase(middle) && pointer.getLast().equalsIgnoreCase(last) && pointer.getSurname().equalsIgnoreCase(surname) && pointer.getSchool().equalsIgnoreCase(school.getName())) {
					replaceWith(pointer.getJudge());
					return;
				}
			}
		}
	}

	public void replaceWith(Judge value) {
		id = value.getId();
		first = value.getFirst();
		middle = value.getMiddle();
		last = value.getLast();
		surname = value.getSurname();
		school = value.getSchool();
	}

	public void replaceNull(Judge value) {
		if(id == null)
			id = value.getId();
		if(first == null)
			first = value.getFirst();
		if(middle == null)
			middle = value.getMiddle();
		if(last == null)
			last = value.getLast();
		if(surname == null)
			surname = value.getSurname();
		if(school == null)
			school = value.getSchool();
	}

	public boolean equals(Judge judge) {
		if(judge == null)
			return false;
		else if(id != null && judge.getId() != null)
			return id.equals(judge.getId());
		else
			return equalsIgnoreID(judge);
	}

	public boolean equalsIgnoreID(Judge judge) {
		if(judge == null)
			return false;
		String first = judge.getFirst();
		String last = judge.getLast();
		School school = judge.getSchool();
		if(this.school != null && school != null && !this.school.equals(school)) {
			return false;
		} else if(((first == null && this.first == null) || (this.first != null && cleanString(this.first).equals(cleanString(first)))) &&
				(((this.last == null || last == null) || (this.last != null && cleanString(this.last).equals(cleanString(last)))))) {
			replaceNull(judge);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This object shall be replaced with an existing object in the collection. If no object is found, this object will be saved into the collection
	 * @return Found an object in the collection
	 */
	public boolean updateToDocument(Datastore datastore, MongoCollection<Judge> judgeCollection, MongoCollection<School> schoolCollection) {
		if(school != null && school.getName() != null)
			school.updateToDocument(datastore, schoolCollection);
		FindIterable<Judge> judgesFound = judgeCollection.find(Filters.eq("last", last));
		for(Judge d : judgesFound) {
			if(equals(d)) {
				d.replaceNull(this); // Get possible missing information
				replaceWith(d);
				return true;
			}
		}
		// not found
		datastore.save(this);
		return false;
	}
	
	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	public String getFirst() {
		return first;
	}

	public void setFirst(String first) {
		this.first = first;
	}

	public String getMiddle() {
		return middle;
	}

	public void setMiddle(String middle) {
		this.middle = middle;
	}

	public String getLast() {
		return last;
	}

	public void setLast(String last) {
		this.last = last;
	}

	public String getSurname() {
		return surname;
	}

	public void setSurname(String surname) {
		this.surname = surname;
	}

	public School getSchool() {
		return school;
	}

	public void setSchool(School school) {
		this.school = school;
	}

	@Id
	private ObjectId id;
	private String first, middle, last, surname;
	@Reference
	private School school;
}
