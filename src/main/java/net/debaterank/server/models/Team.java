package net.debaterank.server.models;

import com.mongodb.DBRef;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;

@Entity("teams")
public class Team {

	@Id
	private ObjectId id = new ObjectId();

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	@Reference
	private Debater one;

	public Debater getOne() {
		return one;
	}

	public void setOne(Debater one) {
		this.one = one;
	}

	public Debater getTwo() {
		return two;
	}

	public void setTwo(Debater two) {
		this.two = two;
	}

	public Team() {

	}

	public Team(Debater one, Debater two) {
		this.one = one;
		this.two = two;
	}

	@Reference
	private Debater two;

	/**
	 * This object shall be replaced with an existing object in the collection. If no object is found, this object will be saved into the collection
	 * @return Found an object in the collection
	 */
	public boolean updateToDocument(Datastore datastore, MongoCollection<Team> teamCollection, MongoCollection<Debater> debaterCollection, MongoCollection<School> schoolCollection) {
		Team teamFound = teamCollection.find(Filters.or(
				Filters.and(Filters.eq("one", new DBRef("debaters", one.getId())), Filters.eq("two", new DBRef("debaters", two.getId()))),
				Filters.and(Filters.eq("one", new DBRef("debaters", two.getId())), Filters.eq("two", new DBRef("debaters", one.getId())))))
				.first();
		if(teamFound != null) {
			id = teamFound.getId();
			one = teamFound.getOne();
			two = teamFound.getTwo();
			return true;
		}
		// not found
		datastore.save(this);
		return false;
	}

	public static Team getTeamFromLastName(String lastOne, String lastTwo, School school, MongoCollection<Debater> debaterCollection) {
		Debater one = Debater.getDebaterFromLastName(lastOne, school, debaterCollection);
		Debater two = Debater.getDebaterFromLastName(lastTwo, school, debaterCollection);
		if(one == null || two == null)
			return null;
		else
			return new Team(one, two);
	}

	public boolean equalsByLastName(String one, String two) {
		return (this.one.getLast().equals(one) && this.two.getLast().equals(two)) || (this.one.getLast().equals(two) && this.two.getLast().equals(one));
	}

	public boolean equalsByLastName(Team team) {
		return (one.getLast().equals(team.getOne().getLast()) && two.getLast().equals(team.getTwo().getLast())) || (two.getLast().equals(team.getOne().getLast()) && one.getLast().equals(team.getTwo().getLast()));
	}

	public boolean equals(Team team) {
		return (one.equals(team.getOne()) && two.equals(team.getTwo())) || (two.equals(team.getOne()) && one.equals(team.getTwo()));
	}

}
