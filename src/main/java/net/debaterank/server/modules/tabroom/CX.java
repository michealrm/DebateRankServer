package net.debaterank.server.modules.tabroom;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.debaterank.server.models.*;
import net.debaterank.server.modules.Module;
import net.debaterank.server.modules.WorkerPool;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mongodb.morphia.Datastore;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

import static net.debaterank.server.util.NetIOHelper.getInputStream;
import static net.debaterank.server.util.NetIOHelper.readJsonFromInputStream;

public class CX extends Module {

	private ArrayList<TabroomEntryInfo> tournaments;
	private WorkerPool manager;
	private MongoCollection<Debater> debaterCollection;
	private MongoCollection<School> schoolCollection;
	private MongoCollection<Judge> judgeCollection;
	private MongoCollection<Team> teamCollection;

	public CX(ArrayList<TabroomEntryInfo> tournaments, WorkerPool manager, Datastore datastore, MongoDatabase db) {
		super(LogManager.getLogger(net.debaterank.server.modules.jot.CX.class), datastore, db);
		debaterCollection = db.getCollection("debaters", Debater.class);
		schoolCollection = db.getCollection("schools", School.class);
		judgeCollection = db.getCollection("judges", Judge.class);
		teamCollection = db.getCollection("teams", Team.class);
		this.tournaments = tournaments;
		this.manager = manager;
	}

	public void run() {
		for(TabroomEntryInfo tInfo : tournaments) {
			manager.newModule(() -> {
				try {
					enterTournament(datastore, getInputStream(tInfo.getEndpoint(), log), tInfo.getTournament(), tInfo.getTourn_id(), tInfo.getEvent_id());
				} catch(Exception e) {
					e.printStackTrace();
				}
			});
		}
	}

	private void enterTournament(Datastore datastore, BufferedInputStream iStream, Tournament t, int tourn_id, int event_id) throws IOException, JSONException {
		JSONObject jsonObject = readJsonFromInputStream(iStream);

		log.info("Updating " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + event_id);

		// Getting schools
		HashMap<Integer, School> schools = new HashMap<>();
		JSONArray jsonSchool = jsonObject.getJSONArray("school");
		for(int i = 0;i<jsonSchool.length();i++) {
			JSONObject jObject = jsonSchool.getJSONObject(i);
			int id = jObject.getInt("ID");
			School school = new School(jObject.getString("SCHOOLNAME"));
			school.updateToDocument(datastore, schoolCollection);
			schools.put(id, school);
		}

		// Getting competitors / entry students
		HashMap<Integer, Team> competitors = new HashMap<>();
		HashMap<Integer, Debater> entryStudents = new HashMap<>();
		HashMap<Debater, Team> teams = new HashMap<>();
		JSONArray jsonEntry_student = jsonObject.getJSONArray("entry_student");
		for(int i = 0;i<jsonEntry_student.length();i++) {
			JSONObject jObject = jsonEntry_student.getJSONObject(i);
			int entry = jObject.getInt("ENTRY");
			int id = jObject.getInt("ID");
			String first = jObject.getString("FIRST");
			String last = jObject.getString("LAST");
			School school = schools.get(jObject.getInt("SCHOOL"));
			Team team = null;
			if((team = competitors.get(entry)) == null) {
				team = new Team();
				competitors.put(entry, team);
			}
			Debater debater = new Debater(first + " " +last, school);
			debater.updateToDocument(datastore, debaterCollection, schoolCollection);
			teams.put(debater, team);
			entryStudents.put(id, debater);
			if(team.getOne() == null)
				team.setOne(debater);
			else
				team.setTwo(debater);
		}
		for(Team team : competitors.values()) {
			team.updateToDocument(datastore, teamCollection, debaterCollection, schoolCollection);
		}

		// Getting judges
		HashMap<Integer, Judge> judges = new HashMap<>();
		JSONArray jsonJudge = jsonObject.getJSONArray("judge");
		for(int i = 0;i<jsonJudge.length();i++) {
			JSONObject jObject = jsonJudge.getJSONObject(i);
			int id = jObject.getInt("ID");
			String first = jObject.getString("FIRST");
			String last = jObject.getString("LAST");
			School school = schools.get(jObject.getInt("SCHOOL"));
			Judge judge = new Judge(first + " " + last, school);
			judge.updateToDocument(datastore, judgeCollection, schoolCollection);
			judges.put(id, judge);
		}

		// Getting round keys / names
		HashMap<Integer, String> roundStrings = new HashMap<>(); // <Round ID, Round String>
		HashMap<Integer, RoundInfo> roundInfos = new HashMap<>(); // <Round ID, RoundInfo>
		JSONArray jsonRound = jsonObject.getJSONArray("round");
		for(int i = 0;i<jsonRound.length();i++) {
			JSONObject jObject = jsonRound.getJSONObject(i);
			int id = jObject.getInt("ID");
			int rd_name = jObject.getInt("RD_NAME");
			String pairingScheme = jObject.getString("PAIRINGSCHEME");
			RoundInfo info = new RoundInfo();
			info.number = rd_name;
			info.elim = pairingScheme.equals("Elim");
			roundInfos.put(id, info);
		}
		HashMap<Integer, String> roundNumberToEntry = roundToFriendlyRound(new ArrayList<>(roundInfos.values()));
		for(Map.Entry<Integer, RoundInfo> entry : roundInfos.entrySet()) {
			roundStrings.put(entry.getKey(), roundNumberToEntry.get(entry.getValue().number));
		}

		// Getting panels
		HashMap<Integer, Round> panels = new HashMap<>();
		JSONArray jsonPanel = jsonObject.getJSONArray("panel");
		for(int i = 0;i<jsonPanel.length();i++) {
			JSONObject jObject = jsonPanel.getJSONObject(i);
			int id = jObject.getInt("ID");
			int round = jObject.getInt("ROUND");
			boolean bye = jObject.getInt("BYE") == 1;
			Round r = new Round();
			r.setBye(bye);
			r.setRound(roundStrings.get(round));
			r.setBallot(new ArrayList<>());

			panels.put(id, r);
		}

		// Finally, ballot parsing
		HashMap<Integer, Pair<Round, Ballot>> ballots = new HashMap<>();
		JSONArray jsonBallot = jsonObject.getJSONArray("ballot");
		for(int i = 0;i<jsonBallot.length();i++) {
			try {
				JSONObject jObject = jsonBallot.getJSONObject(i);
				int id = jObject.getInt("ID");
				int team = jObject.getInt("ENTRY");
				int panel = jObject.getInt("PANEL");
				int judge = 0;
				try {
					judge = jObject.getInt("JUDGE");
				} catch (JSONException e) {
					judge = -1;
				}
				int side = jObject.getInt("SIDE");
				boolean bye = false;
				if (jObject.has("BYE"))
					bye = jObject.getInt("BYE") == 1;

				Round round = panels.get(panel);
				if (round == null) {
					log.warn("Panel " + panel + " in " + t.getLink() + " was null! Skipping this ballot");
					continue;
				}
				if (side == 1 && round.getTeamAff() == null)
					round.setTeamAff(competitors.get(team));
				else if (side == 2 && round.getTeamNeg() == null)
					round.setTeamNeg(competitors.get(team));
				else if (side == -1) {
					round.setTeamAff(competitors.get(team));
					round.setTeamNeg(competitors.get(team));
					round.setNoSide(true);
				}
				round.setBye(round.isBye() || bye);

				Ballot ballot = new Ballot();
				ballot.setJudge(judges.get(judge)); // This can be null

				ballots.put(id, Pair.of(round, ballot));
			} catch(JSONException e) {}
		}

		// Round results
		JSONArray jsonBallot_score = jsonObject.getJSONArray("ballot_score");
		for(int i = 0;i<jsonBallot_score.length();i++) {
			try {
				JSONObject jObject = jsonBallot_score.getJSONObject(i);
				int ballotID = jObject.getInt("BALLOT");
				int recipient = jObject.getInt("RECIPIENT");
				String score_id = jObject.getString("SCORE_ID");
				double score = jObject.getDouble("SCORE");
				int id = jObject.getInt("ID");

				Pair<Round, Ballot> ballot = ballots.get(ballotID);
				if(ballot == null) {
					log.warn("Ballot " + ballotID + " in " + t.getLink() + " is null. Skipping ballot score");
					continue;
				}

				Team aff = ballot.getLeft().getTeamAff();
				Team neg = ballot.getLeft().getTeamNeg();

				if(score_id.equals("WIN")) { // WIN RECIPIENT is the team / entry ID
					Team team = competitors.get(recipient);
					if((team == aff && score == 1.0) || (team == neg && score == 0.0))
						ballot.getRight().setDecision("Aff");
					else if((team == neg && score == 1.0) || (team == aff && score == 0.0))
						ballot.getRight().setDecision("Neg");
				} else if(score_id.equals("POINTS")) { // POINTS RECIPIENT is the entry_student ID
					Debater debater = entryStudents.get(recipient);
					Team team = teams.get(debater);
					if(team == aff) {
						if(team.getOne() == debater)
							ballot.getRight().setAff1_speaks(score);
						if(team.getTwo() == debater)
							ballot.getRight().setAff2_speaks(score);
					}
					if(team == neg) {
						if(team.getOne() == debater)
							ballot.getRight().setNeg1_speaks(score);
						if(team.getTwo() == debater)
							ballot.getRight().setNeg2_speaks(score);
					}
				} else if(score_id.equals("RANK")) { // POINTS RECIPIENT is the entry_student ID
					Debater debater = entryStudents.get(recipient);
					Team team = teams.get(debater);
					if(team == aff) {
						if(team.getOne() == debater)
							ballot.getRight().setAff1_place((int)score);
						if(team.getTwo() == debater)
							ballot.getRight().setAff2_place((int)score);
					}
					if(team == neg) {
						if(team.getOne() == debater)
							ballot.getRight().setNeg1_place((int)score);
						if(team.getTwo() == debater)
							ballot.getRight().setNeg2_place((int)score);
					}
				} else {
					log.warn("Ballot score " + id + " in " + t.getLink() + " contains an invalid recipient. Skipping");
				}
			} catch(JSONException e) {}
		}

		// Collapse ballots to one judge per ballot
		ArrayList<Round> collBallots = new ArrayList<>();
		for(Pair<Round, Ballot> pair : ballots.values()) {
			Round round = pair.getLeft();
			Ballot ballot = pair.getRight();
			if(!collBallots.contains(round))
				collBallots.add(round);
			boolean containsBallot = false;
			for(Ballot b : round.getBallot()) {
				if(b.getJudge() == ballot.getJudge()) {
					containsBallot = true;
					b.replaceNull(ballot);
				}
			}
			if(!containsBallot)
				round.getBallot().add(ballot);
		}

		// Update database
		if(collBallots.size() == 0) {
			log.info(t.getName() + " had no CX entries. " + t.getLink());
			t.getRounds_contains().put("cx", false);
		} else {
			t.setCx_rounds(collBallots);
			t.getRounds_contains().put("cx", true);
		}
		datastore.save(t);
		log.info("Updated " + t.getName());

	}

	private HashMap<Integer, String> roundToFriendlyRound(List<RoundInfo> infos) {
		String[] elimsStrings = {"TO", "DO", "O", "Q", "S", "F"};
		Collections.sort(infos, (o1, o2) -> o1.number - o2.number);
		ArrayList<RoundInfo> infosCopy = new ArrayList<>(infos);
		ArrayList<Pair<Integer, String>> elims = new ArrayList<>();
		for(int i = 0;i<infosCopy.size();i++)
			if(infosCopy.get(i).elim) {
				elims.add(Pair.of(infosCopy.get(i).number, null));
				infosCopy.remove(i--);
			}
		for(int i = 0;i<elims.size();i++)
			elims.set(i, Pair.of(elims.get(i).getLeft(), elimsStrings[elimsStrings.length - (elims.size() - i)]));
		HashMap<Integer, String> ret = new HashMap<>();
		for(RoundInfo info : infosCopy)
			ret.put(info.number, String.valueOf(info.number));
		for(Pair<Integer, String> pair : elims)
			ret.put(pair.getLeft(), pair.getRight());
		return ret;
	}
}
