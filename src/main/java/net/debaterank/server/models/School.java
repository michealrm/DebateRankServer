package net.debaterank.server.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.debaterank.server.Server;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;

import java.io.Serializable;

@Entity("schools")
public class School implements Serializable {

	@Id
	private ObjectId id = new ObjectId();
	@Property("name")
	private String name;
	@Property("address")
	private String address;
	@Property("state")
	private String state;
	@Property("nsda_link")
	private String nsda_link;

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String link) {
		this.address = link;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getNsda_link() {
		return nsda_link;
	}

	public void setNsda_link(String nsda_link) {
		this.nsda_link = nsda_link;
	}

	public School(String name) {
		this.name = name;
	}

	public School(String name, String address, String state, String nsda_link) {
		this.name = name;
		this.address = address;
		this.state = state;
		this.nsda_link = nsda_link;
	}

	public School(ObjectId id, String name, String address, String state, String nsda_link) {
		this.id = id;
		this.name = name;
		this.address = address;
		this.state = state;
		this.nsda_link = nsda_link;
	}

	public School() {}

	public void replaceNull(School school) {
		if(id == null)
			id = school.getId();
		if(name == null)
			name = school.getName();
		if(nsda_link == null)
			nsda_link = school.getNsda_link();
		if(address == null)
			address = school.getAddress();
		if(state == null)
			state = school.getState();
	}

	public void replaceWith(School school) {
		id = school.getId();
		name = school.getName();
		nsda_link = school.getNsda_link();
		address = school.getAddress();
		state = school.getState();
	}

	public boolean equals(School school) {
		return (state != null && state.equals(school.getState())) || (nsda_link != null && nsda_link.equals(school.getNsda_link())) ||
				(address != null && address.equals(school.getAddress())) || (name != null && name.equals(school.getName())) ||
				(id != null && id.equals(school.getId()));
	}

	/**
	 * This object shall be replaced with an existing object in the collection. If no object is found, this object will be saved into the collection
	 * @return Found an object in the collection
	 */
	public boolean updateToDocument(Datastore datastore, MongoCollection<School> schoolCollection) {
		School store = null;
		if((store = Server.schoolStore.get(name)) != null) {
			replaceWith(store);
			return true;
		}
		FindIterable<School> schoolsFound = schoolCollection.find(Filters.or(Filters.eq("name", name), Filters.eq("address", address), Filters.eq("nsda_link", nsda_link)));
		for(School s: schoolsFound) {
			if(equals(s)) {
				s.replaceNull(this); // Get possible missing information
				replaceWith(s);
				Server.schoolStore.put(name, this);
				return true;
			}
		}
		// not found
		datastore.save(this);
		return false;
	}

}
