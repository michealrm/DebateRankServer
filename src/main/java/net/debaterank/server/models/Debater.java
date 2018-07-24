package net.debaterank.server.models;

import com.mongodb.DBRef;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.debaterank.server.Server;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.annotations.Reference;

import java.io.Serializable;

import static net.debaterank.server.util.DRHelper.isSameName;

@Entity("debaters")
public class Debater implements Serializable {

	public Debater() {}

	public Debater(ObjectId id, String first, String middle, String last, String surname, School school) {
		this.id = id;
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.surname = surname;
		this.school = school;
		changeInfoIfMatchesPointer();
	}

	public Debater(String first, String middle, String last, String surname, School school) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.surname = surname;
		this.school = school;
		changeInfoIfMatchesPointer();
	}

	public Debater(String name, String school) {
		name = name.trim().replaceAll(" \\(.+?\\)", "");
		School oSchool = new School();
		if(school != null) {
			oSchool.setName(school.trim().equals(" ") ? null : school.trim());
			this.school = oSchool;
		}
		else {
			this.school = oSchool;
		}
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

	public Debater(String name, School school) {
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
		if(Server.debaterPointers != null) {
			for(DebaterPointer pointer : Server.debaterPointers) {
				if (pointer.getFirst().equalsIgnoreCase(first) && pointer.getMiddle().equalsIgnoreCase(middle) && pointer.getLast().equalsIgnoreCase(last) && pointer.getSurname().equalsIgnoreCase(surname) && pointer.getSchool().equalsIgnoreCase(school.getName())) {
					replaceWith(pointer.getDebater());
					return;
				}
			}
		}
	}

	public void replaceWith(Debater value) {
		id = value.getId();
		first = value.getFirst();
		middle = value.getMiddle();
		last = value.getLast();
		surname = value.getSurname();
		school = value.getSchool();
	}

	public void replaceNull(Debater value) {
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

	public boolean equals(Debater debater) {
		if(debater == null)
			return false;
		String first = debater.getFirst();
		String last = debater.getLast();
		School school = debater.getSchool();
		if(this.school != null && school != null && !this.school.equals(school)) {
			return false;
		} else if(isSameName(this.first, first) && isSameName(this.last, last)) {
			replaceNull(debater);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This object shall be replaced with an existing object in the collection. If no object is found, this object will be saved into the collection
	 * @return Found an object in the collection
	 */
	public boolean updateToDocument(Datastore datastore, MongoCollection<Debater> debaterCollection, MongoCollection<School> schoolCollection) {
		if(school != null && school.getName() != null)
			school.updateToDocument(datastore, schoolCollection);
		FindIterable<Debater> debatersFound = debaterCollection.find(Filters.eq("last", last));
		for(Debater d : debatersFound) {
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

	public static Debater getDebaterFromLastName(String last, School school, MongoCollection<Debater> debaterCollection) {
		// .first() may be problematic
		Debater debaterFound = debaterCollection.find(Filters.and(Filters.eq("last", last), Filters.eq("school", new DBRef("schools", school.getId())))).first();
		if(debaterFound != null)
			return debaterFound;
		else
			return null;
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
	private ObjectId id = new ObjectId();
	@Property("first")
	private String first;
	@Property("middle")
	private String middle;
	@Property("last")
	private String last;
	@Property("surname")
	private String surname;
	@Reference
	private School school;
}
