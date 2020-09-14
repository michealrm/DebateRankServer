package net.debaterank.server.modules.tabroom;

import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.EntryInfo;
import net.debaterank.server.util.HibernateUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;

import static net.debaterank.server.util.DRHelper.*;

/**
 *
 * @param <E> The event class
 * @param <R> The round class
 * @param <B> The ballot class
 */
public class DuoEvent<E, R extends DuoRound, B extends DuoBallot<R>> implements Runnable {

    private Logger log;
    private ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tournaments;
    private WorkerPool manager;
    private String event;
    private Class<R> roundClass;
    private Class<B> ballotClass;

    public DuoEvent(Class<E> event, Class<R> r, Class<B> b, ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tournaments, WorkerPool manager) {
        this.event = event.getSimpleName();
        roundClass = r;
        ballotClass = b;
        log = LogManager.getLogger(event);
        this.tournaments = tournaments;
        this.manager = manager;
    }

    private R newRound(Tournament t) {
        R r = null;
        try {
            r = roundClass.newInstance();
        } catch(Exception e) {
            log.error(e);
            return null;
        }
        r.setTournament(t);
        return r;
    }

    private B newBallot(R r) {
        B b = null;
        try {
            b = ballotClass.newInstance();
        } catch(Exception e) {
            log.error(e);
            return null;
        }
        b.setRound(r);
        return b;
    }


    public void run() {
        for(EntryInfo<EntryInfo.TabroomEventInfo> tInfo : tournaments) {
            if(tInfo.getTournament().isScraped(event) || tInfo.getEventRows(event).isEmpty()) continue;
            ArrayList<EntryInfo.TabroomEventInfo> rows = tInfo.getEventRows(event);
            for(EntryInfo.TabroomEventInfo row : rows) {
                manager.newModule(() -> {
                    try {
                        enterTournament(tInfo.getTournament(), row);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    private void enterTournament(Tournament t, EntryInfo.TabroomEventInfo ei) throws IOException, JSONException {
        Session session = HibernateUtil.getSession();
        try {
            Transaction transaction = session.beginTransaction();
            BufferedInputStream iStream = getInputStream(ei.endpoint, log);
            JSONObject jsonObject = readJsonObjectFromInputStream(iStream);
            int tourn_id = ei.tourn_id;
            int event_id = ei.event_id;

            log.info("Updating " + t.getName() + ". Tournament ID: " + tourn_id + " Event ID: " + this.event + event_id);

            // Getting schools
            HashMap<Integer, School> schools = new HashMap<>();
            JSONArray jsonSchool = jsonObject.getJSONArray("school");
            for (int i = 0; i < jsonSchool.length(); i++) {
                JSONObject jObject = jsonSchool.getJSONObject(i);
                int id = jObject.getInt("ID");
                School school = new School(jObject.getString("SCHOOLNAME"));
                schools.put(id, school);
            }

            // Getting competitors / entry students
            HashMap<Integer, Team> competitors = new HashMap<>();
            HashMap<Integer, Debater> entryStudents = new HashMap<>();
            HashMap<Debater, Team> teams = new HashMap<>();
            JSONArray jsonEntry_student = jsonObject.getJSONArray("entry_student");
            for (int i = 0; i < jsonEntry_student.length(); i++) {
                JSONObject jObject = jsonEntry_student.getJSONObject(i);
                int entry = jObject.getInt("ENTRY");
                int id = jObject.getInt("ID");
                String first = jObject.getString("FIRST");
                String last = jObject.getString("LAST");
                School school = null;
                try {
                    String schoolStr = jObject.getString("SCHOOL");
                    int schoolId = Integer.parseInt(schoolStr);
                    school = schools.get(schoolId);
                } catch(Exception e) {
                    log.warn("Could not convert  " + jObject.get("SCHOOL") + " to an integer");
                }
                Team team;
                if ((team = competitors.get(entry)) == null) {
                    team = new Team();
                    competitors.put(entry, team);
                }
                Debater debater = new Debater(first + " " + last, school);
                debater = Debater.getDebaterOrInsert(debater);
                teams.put(debater, team);
                entryStudents.put(id, debater);
                if (team.getOne() == null)
                    team.setOne(debater);
                else
                    team.setTwo(debater);
            }
            for (Map.Entry<Integer, Team> es : competitors.entrySet()) {
                es.setValue(Team.getTeamOrInsert(es.getValue()));
            }

            // Getting judges
            HashMap<Integer, Judge> judges = new HashMap<>();
            JSONArray jsonJudge = jsonObject.getJSONArray("judge");
            for (int i = 0; i < jsonJudge.length(); i++) {
                JSONObject jObject = jsonJudge.getJSONObject(i);
                int id = jObject.getInt("ID");
                String first = jObject.getString("FIRST");
                String last = jObject.getString("LAST");
                School school = schools.get(jObject.getInt("SCHOOL"));
                Judge judge = new Judge(first + " " + last, school);
                judge = Judge.getJudgeOrInsert(judge);
                judges.put(id, judge);
            }

            // Getting round keys / names
            HashMap<Integer, String> roundStrings = new HashMap<>(); // <DuoRound ID, DuoRound String>
            HashMap<Integer, RoundInfo> roundInfos = new HashMap<>(); // <DuoRound ID, RoundInfo>
            JSONArray jsonRound = jsonObject.getJSONArray("round");
            for (int i = 0; i < jsonRound.length(); i++) {
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
            for (Map.Entry<Integer, RoundInfo> entry : roundInfos.entrySet()) {
                roundStrings.put(entry.getKey(), roundNumberToEntry.get(entry.getValue().number));
            }

            // Getting panels
            HashMap<Integer, R> panels = new HashMap<>();
            JSONArray jsonPanel = jsonObject.getJSONArray("panel");
            for (int i = 0; i < jsonPanel.length(); i++) {
                JSONObject jObject = jsonPanel.getJSONObject(i);
                int id = jObject.getInt("ID");
                int round = jObject.getInt("ROUND");
                boolean bye = jObject.getInt("BYE") == 1;
                R r = newRound(t);
                r.setBye(bye);
                r.setRound(roundStrings.get(round));
                r.setAbsUrl(t.getLink());

                panels.put(id, r);
            }

            // Finally, ballot parsing
            HashMap<Integer, Pair<R, B>> ballots = new HashMap<>();
            JSONArray jsonBallot = jsonObject.getJSONArray("ballot");
            for (int i = 0; i < jsonBallot.length(); i++) {
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

                    R round = panels.get(panel);
                    if (round == null) {
                        log.warn("Panel " + panel + " in " + t.getLink() + " was null! Skipping this ballot");
                        continue;
                    }
                    if (side == 1 && round.getA() == null)
                        round.setA(competitors.get(team));
                    else if (side == 2 && round.getN() == null)
                        round.setN(competitors.get(team));
                    else if (side == -1) {
                        round.setA(competitors.get(team));
                        round.setN(competitors.get(team));
                        // round.setNoSide(true);
                    }
                    round.setBye(round.isBye() || bye);

                    B ballot = newBallot(round);
                    ballot.setJudge(judges.get(judge)); // This can be null

                    ballots.put(id, Pair.of(round, ballot));
                } catch (JSONException e) {
                }
            }

            // DuoRound results
            JSONArray jsonBallot_score = jsonObject.getJSONArray("ballot_score");
            for (int i = 0; i < jsonBallot_score.length(); i++) {
                try {
                    JSONObject jObject = jsonBallot_score.getJSONObject(i);
                    int ballotID = jObject.getInt("BALLOT");
                    int recipient = jObject.getInt("RECIPIENT");
                    String score_id = jObject.getString("SCORE_ID");
                    double score = jObject.getDouble("SCORE");
                    int id = jObject.getInt("ID");

                    Pair<R, B> ballot = ballots.get(ballotID);
                    if (ballot == null) {
                        log.warn("B " + ballotID + " in " + t.getLink() + " is null. Skipping ballot score");
                        continue;
                    }

                    Team aff = ballot.getLeft().getA();
                    Team neg = ballot.getLeft().getN();

                    if (score_id.equals("WIN")) { // WIN RECIPIENT is the team / entry ID
                        Team team = competitors.get(recipient);
                        if ((team == aff && score == 1.0) || (team == neg && score == 0.0))
                            ballot.getRight().setDecision("Aff");
                        else if ((team == neg && score == 1.0) || (team == aff && score == 0.0))
                            ballot.getRight().setDecision("Neg");
                    } else if (score_id.equals("POINTS")) { // POINTS RECIPIENT is the entry_student ID
                        Debater debater = entryStudents.get(recipient);
                        Team team = teams.get(debater);
                        if(team == null) {
                            continue;
                        }
                        if (team == aff) {
                            if (team.getOne() == debater)
                                ballot.getRight().setA1_s(score);
                            if (team.getTwo() == debater)
                                ballot.getRight().setA2_s(score);
                        }
                        if (team == neg) {
                            if (team.getOne() == debater)
                                ballot.getRight().setN1_s(score);
                            if (team.getTwo() == debater)
                                ballot.getRight().setN2_s(score);
                        }
                    } else if (score_id.equals("RANK")) { // POINTS RECIPIENT is the entry_student ID
                        Debater debater = entryStudents.get(recipient);
                        Team team = teams.get(debater);
                        if(team == null) {
                            continue;
                        }
                        if (team == aff) {
                            if (team.getOne() == debater)
                                ballot.getRight().setA1_p((int) score);
                            if (team.getTwo() == debater)
                                ballot.getRight().setA2_p((int) score);
                        }
                        if (team == neg) {
                            if (team.getOne() == debater)
                                ballot.getRight().setN1_p((int) score);
                            if (team.getTwo() == debater)
                                ballot.getRight().setN2_p((int) score);
                        }
                    } else {
                        log.warn("B score " + id + " in " + t.getLink() + " contains an invalid recipient. Skipping");
                    }
                } catch (JSONException e) {
                }
            }

            // Collapse ballots to one judge per ballot
            ArrayList<B> collBallots = new ArrayList<>();
            for (Pair<R, B> pair : ballots.values()) {
                B ballot = pair.getRight();
                if (!collBallots.contains(ballot))
                    collBallots.add(ballot);
                for (B b : collBallots) {
                    if (b.getJudge() == ballot.getJudge())
                        replaceNull(b, ballot);
                }
            }

            // Update database
            int i = 0;
            for(B ballot : collBallots) {
                session.persist(ballot);
            }
            t.setScraped(event, true);
            session.merge(t);
            transaction.commit();
            log.info("Updated " + t.getName());
        } finally {
            session.close();
        }
    }

    private HashMap<Integer, String> roundToFriendlyRound(List<RoundInfo> infos) {
        String[] elimsStrings = {"TO", "DO", "O", "Q", "S", "F"};
        Collections.sort(infos, Comparator.comparingInt(o -> o.number));
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