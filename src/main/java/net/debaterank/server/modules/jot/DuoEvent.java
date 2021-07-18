package net.debaterank.server.modules.jot;
import net.debaterank.server.models.*;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.EntryInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.debaterank.server.util.JOTHelper.getBracketRound;

/**
 *
 * @param <E> The event class
 * @param <R> The round class
 * @param <B> The ballot class
 */
public class DuoEvent<E, R extends DuoRound, B extends DuoBallot<R>> implements Runnable {

    private Logger log;
    private ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments;
    private WorkerPool manager;
    private String event;
    private Class<R> roundClass;
    private Class<B> ballotClass;

    public DuoEvent(Class<E> event, Class<R> r, Class<B> b, ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> tournaments, WorkerPool manager) {
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
        // Scrape events per tournament
        for(EntryInfo tInfo : tournaments) {
            Tournament t = tInfo.getTournament();
            if(t.isScraped(event) || tInfo.getEventRows(event).isEmpty()) continue;
            manager.newModule(() -> {
                Session session = HibernateUtil.getSession();
                try {
                    Transaction transaction = session.beginTransaction();
                    ArrayList<B> ballots = new ArrayList<>();
                    ArrayList<R> tournRounds = new ArrayList<>();
                    ArrayList<EntryInfo.JOTEventLinks> eventRows = tInfo.getEventRows(event);
                    ArrayList<Team> competitorsList = new ArrayList<>();
                    log.info("Updating " + t.getName() + " " + t.getLink());
                    for(EntryInfo.JOTEventLinks eventRow : eventRows) {
                        // Prelims
                        if(eventRow.prelims != null) {
                            Document p = null;
                            try {
                                p = Jsoup.connect(eventRow.prelims).timeout(10*1000).get();
                            } catch(UnsupportedMimeTypeException e) {
                                log.info("Prelims type unsupported: " + e.getMimeType() + ". Skipping.");
                                break;
                            }
                            Element table = p.select("table[border=1]").first();
                            Elements rows = table.select("tr:has(table)");

                            // Register all debaters
                            HashMap<String, Team> competitors = new HashMap<String, Team>();
                            for(Element row : rows) {
                                Element info = row.select("td").first();
                                String left = info.select("tr:eq(0)").text();
                                String second = info.select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ");
                                String key = second.split(" ")[0];
                                String school = second.split(" ").length > 1 ? (second.substring(second.indexOf(' ') + 1)).trim() : null;
                                String right = info.select("tr:eq(2)").text();
                                Team team = new Team(new Debater(left, school), new Debater(right, school));
                                team = Team.getTeamOrInsert(team);
                                competitors.put(key, team);
                                competitorsList.add(team);
                            }

                            // Parse rounds
                            ArrayList<R> rounds = new ArrayList<>();
                            for(int i = 0;i<rows.size();i++) {
                                String key = rows.get(i).select("td").first().select("tr:eq(1)").text().replaceAll("\u00a0|&nbsp", " ").split(" ")[0];
                                Team team = competitors.get(key);
                                if(team == null) {
                                    log.warn("Couldn't find " + key + " in the competitors hashmap. " + t.getLink());
                                    continue;
                                }
                                Elements cols = rows.get(i).select("td[width=80]");
                                round:
                                for(int k = 0;k<cols.size();k++) {
                                    Element top, win, side, against, bot, speaks1, speaks2, place1, place2;
                                    try {
                                        top = cols.get(k).select("tr:eq(0)").first();
                                        speaks1 = top.select("td[align=left]").first();
                                        against = top.select("td[align=center]").first();
                                        place1 = top.select("td[align=right]").first();

                                        win = cols.get(k).select(".rec").first();

                                        bot = cols.get(k).select("tr:eq(2)").first();
                                        speaks2 = bot.select("td[align=left]").first();
                                        side = bot.select("td[align=center]").first();
                                        place2 = bot.select("td[align=right]").first();

                                    }
                                    catch(Exception e) {
                                        continue;
                                    }
                                    R round = newRound(t);
                                    Team againstTeam = null;
                                    if(win == null || win.text() == null || against == null) {
                                        continue;
                                    }

                                    if(win.text().equals("B") || win.text().equals("F")) {
                                        // bye
                                        if(win.text().equals("B")) {
                                            round.setA(team);
                                            round.setN(team);
                                        } else {
                                            B ballot = newBallot(round);
                                            if(side == null || side.text() == null || side.text().equals("Pro") || side.text().equals("Aff")) {
                                                round.setA(team);
                                                ballot.setDecision("Neg");
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setA1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setA2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setA1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setA2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                            }
                                            else {
                                                round.setN(team);
                                                ballot.setDecision("Aff");
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setN1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setN2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setN1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setN2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                            }
                                            ballots.add(ballot);
                                        }
                                        round.setBye(true);
                                        round.setRound(String.valueOf(k+1));
                                        round.setAbsUrl(p.baseUri());
                                        rounds.add(round);
                                    } else if(win.text() != null && side != null && side.text() != null && (side.text().equals("Pro") || side.text().equals("Con") || side.text().equals("Aff") || side.text().equals("Neg"))) {
                                        // check if other side (aff / neg) is competitors.get(against.text()) win.text().equals("F")
                                        for(B ballot : ballots) {
                                            R r = ballot.getRound();
                                            if(r.getA() != null && r.getA().equals(team) && r.getRound().equals(String.valueOf(k+1))) {
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setA1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setA2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setA1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setA2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                                continue round;
                                            } else if(r.getN() != null && r.getN().equals(team) && r.getRound().equals(String.valueOf(k+1))) {
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setN1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setN2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setN1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setN2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                                continue round;
                                            }
                                        }
                                        // no existing document found. we need to make a new one
                                        if ((win.text().equals("W") || win.text().equals("L")) && against.text() != null && (againstTeam = competitors.get(against.text())) != null) {
                                            B ballot = newBallot(round);
                                            if (side.text().equals("Pro") || side.text().equals("Aff")) {
                                                round.setA(team);
                                                round.setN(againstTeam);
                                                ballot.setDecision(win.text().equals("W") ? "Aff" : "Neg");
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setA1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setA2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setA1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setA2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                            } else { // neg
                                                round.setA(againstTeam);
                                                round.setN(team);
                                                ballot.setDecision(win.text().equals("W") ? "Neg" : "Aff");
                                                try {
                                                    if(speaks1.text() != null)
                                                        ballot.setN1_s(Double.parseDouble(speaks1.text().replaceAll("\\\\*", "")));
                                                    if(speaks2.text() != null)
                                                        ballot.setN2_s(Double.parseDouble(speaks2.text().replaceAll("\\\\*", "")));
                                                } catch(NumberFormatException nfe) {}
                                                try {
                                                    if(place1.text() != null)
                                                        ballot.setN1_p(Integer.parseInt(place1.text()));
                                                    if(place2.text() != null)
                                                        ballot.setN2_p(Integer.parseInt(place2.text()));
                                                } catch(NumberFormatException nfe) {}
                                            }
                                            ballots.add(ballot);
                                            round.setRound(String.valueOf(k + 1));
                                            round.setAbsUrl(p.baseUri());
                                            rounds.add(round);
                                        }
                                    } else {
                                        log.warn("Not logging a round. " + key + " " + (win == null ? null : win.text()) + " " + p.baseUri());
                                    }
                                }
                            }
                            tournRounds.addAll(rounds); // add results
                        }

                        // Double Octos
                        if(eventRow.doubleOctas != null) {
                            Document doc = Jsoup.connect(eventRow.doubleOctas).timeout(10*1000).get();

                            Pattern pattern = Pattern.compile("[^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\) def. [^\\s]+ ([A-Za-z]+?) - ([A-Za-z]+?)( \\((.+?)\\))? \\((Aff|Neg)\\)");
                            doc.getElementsByTag("font").unwrap();
                            Matcher matcher = pattern.matcher(doc.toString().replaceAll("<br>", ""));
                            ArrayList<R> rounds = new ArrayList<>();
                            while(matcher.find()) {

                                String leftDebater = matcher.group(1);
                                String rightDebater = matcher.group(2);
                                String debaterSchool = matcher.group(4);
                                String leftAgainst = matcher.group(6);
                                String rightAgainst = matcher.group(7);
                                String againstSchool = matcher.group(9);

                                if(debaterSchool == null || againstSchool == null || leftDebater == null || rightDebater == null || leftAgainst == null || rightAgainst == null) {
                                    log.warn("null in DO! Skipping round " + t.getLink());
                                    continue;
                                }

                                if(debaterSchool == null || againstSchool == null) {
                                    log.warn("School null in DO! Skipping round " + t.getLink());
                                    continue;
                                }

                                Team team = getTeamFromLastName(competitorsList, leftDebater, rightDebater, debaterSchool);
                                Team against = getTeamFromLastName(competitorsList, leftAgainst, rightAgainst, againstSchool);

                                if(team == null || against == null) {
                                    log.warn("Team null in DO! " + Arrays.asList(leftDebater, rightDebater, leftAgainst, rightAgainst, debaterSchool, againstSchool)+ "Skipping round " + t.getLink());
                                    continue;
                                }

                                R round = newRound(t);
                                round.setAbsUrl(doc.baseUri());
                                if(matcher.group(5).equals("Pro") || matcher.group(5).equals("Aff")) {
                                    round.setA(team);
                                    round.setN(against);
                                } else {
                                    round.setA(against);
                                    round.setN(team);
                                }
                                B ballot = newBallot(round);
                                ballot.setDecision(matcher.group(5));
                                round.setRound("DO");
                                rounds.add(round);
                            }
                            tournRounds.addAll(rounds); // add results
                        }

                        //Bracket
                        if(eventRow.bracket != null) {
                            Document doc = null;
                            try {
                                doc = Jsoup.connect(eventRow.bracket).timeout(10*1000).get();
                            } catch(UnsupportedMimeTypeException e) {
                                log.info("Brackets tyep unsupported: " + e.getMimeType() + ". Skipping.");
                                break;
                            }

                            // Parse rounds
                            ArrayList<R> rounds = new ArrayList<>();
                            String roundStr = null, last = null;
                            ArrayList<Pair<Team, Team>> matchup = new ArrayList<Pair<Team, Team>>();
                            for(int i = 0;(roundStr = getBracketRound(doc, i)) != null;i++) {

                                ArrayList<Pair<Team, Team>> currentMatchup = new ArrayList<Pair<Team, Team>>();

                                if(last != null && last.equals("F")) {
                                    Element element = doc.select("table[cellspacing=0] > tbody > tr > td.top:eq(" + i + ")").first();
                                    Element team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
                                    String[] names = team.text().substring(team.text().indexOf(' ') + 1).split(" - ");
                                    if(names.length != 2)
                                        continue;
                                    currentMatchup.add(Pair.of(new Team(new Debater(null, null, names[0], null, null), new Debater(null, null, names[1], null, null)), (Team)null));
                                }
                                else {
                                    // Add all debaters to an arraylist of pairs
                                    Elements col = doc.select("table[cellspacing=0] > tbody > tr > td:eq(" + i + ")");
                                    Element left = null;
                                    for(Element element : col) {
                                        Element team = null;
                                        if(element.hasClass("btm") || element.hasClass("botr"))
                                            team = element;
                                        else if(element.hasClass("top") || element.hasClass("topr"))
                                            team = element.parent().previousElementSibling().select("td:eq(" + i + ")").first();
                                        else
                                            continue;
                                        if(left == null)
                                            left = team;
                                        else {
                                            try {
                                                left.childNode(0).toString();
                                                team.childNode(0).toString();
                                            } catch(Exception e) {
                                                continue;
                                            }
                                            String leftSchool = null,
                                                    rightSchool = null,
                                                    leftText = left.childNode(0).toString(),
                                                    rightText = team.childNode(0).toString();
                                            if(left.childNodeSize() > 2)
                                                if(left.childNode(2) instanceof TextNode)
                                                    leftSchool = left.childNode(2).toString();
                                                else
                                                    leftSchool = left.childNode(2).unwrap().toString();
                                            if(team.childNodeSize() > 2)
                                                if(team.childNode(2) instanceof TextNode)
                                                    rightSchool = team.childNode(2).toString();
                                                else
                                                    rightSchool = team.childNode(2).unwrap().toString();
                                            String[] leftNames = leftText.substring(leftText.indexOf(' ') + 1).split(" - ");
                                            String[] rightNames = rightText.substring(rightText.indexOf(' ') + 1).split(" - ");
                                            if((leftNames.length != 2 && !leftText.contains("&nbsp;")) || (rightNames.length != 2 && !rightText.contains("&nbsp;")))
                                                continue;
                                            Team l;
                                            if(leftText.contains("&nbsp;"))
                                                l = null;
                                            else {
                                                l = getTeamFromLastName(competitorsList, leftNames[0], leftNames[1], leftSchool);
                                            }
                                            Team r;
                                            if(rightText.contains("&nbsp;") || leftNames.length < 2)
                                                r = null;
                                            else {
                                                r = getTeamFromLastName(competitorsList, leftNames[0], leftNames[1], rightSchool);
                                            }
                                            currentMatchup.add(Pair.of(l, r));
                                            left = null;
                                        }
                                    }
                                }

                                if(matchup != null && last != null) {

                                    // Sort matchups into winner/loser pairs
                                    ArrayList<Pair<Team, Team>> winnerLoser = new ArrayList<Pair<Team, Team>>();
                                    for(Pair<Team, Team> winners : currentMatchup)
                                        for(Pair<Team, Team> matchups : matchup) {
                                            if(matchups.getLeft() != null) {
                                                if(winners.getLeft() != null && winners.getLeft().equalsByLastName(matchups.getLeft()))
                                                    winnerLoser.add(matchups);
                                                if(winners.getRight() != null && winners.getRight().equalsByLastName(matchups.getLeft()))
                                                    winnerLoser.add(matchups);
                                            }
                                            if(matchups.getRight() != null) {
                                                if(winners.getLeft() != null && winners.getLeft().equalsByLastName(matchups.getRight()))
                                                    winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
                                                if(winners.getRight() != null && winners.getRight().equalsByLastName(matchups.getRight()))
                                                    winnerLoser.add(Pair.of(matchups.getRight(), matchups.getLeft()));
                                            }
                                        }

                                    for(Pair<Team, Team> pair : winnerLoser) {

                                        if(pair.getLeft() == null || pair.getRight() == null)
                                            continue;

                                        R round = newRound(t);
                                        round.setAbsUrl(doc.baseUri());
                                        round.setA(pair.getLeft());
                                        round.setN(pair.getRight());
                                        B ballot = newBallot(round);
                                        ballot.setDecision("Aff");
                                        ballots.add(ballot);

                                        rounds.add(round);
                                    }
                                }

                                last = roundStr;
                                matchup = currentMatchup;
                            }
                            tournRounds.addAll(rounds); // add results
                        }
                    }
                    for(B b : ballots)
                        session.persist(b);
                    t.setScraped(event, true);
                    session.merge(t);
                    transaction.commit();
                    log.info("Updated " + t.getName());
                } catch(Exception e) {
                    log.error(e);
                    e.printStackTrace();
                    log.fatal("Could not update " + t.getName());
                } finally {
                    session.close();
                }
            });
        }
    }

    private static Team getTeamFromLastName(ArrayList<Team> list, String one, String two, String school) {
        for(Team t : list) {
            if (t != null && t.getOne() != null && t.getTwo() != null && t.getOne().getLast() != null && t.getTwo().getLast() != null && t.getOne().getLast().equals(one) && t.getTwo().getLast().equals(two) && ((t.getOne().getSchool() == null && school == null) || (t.getOne().getSchool() != null && school != null && t.getOne().getSchool().getName().equals(school))))
                return t;
        }
        return null;
    }

    private static Team findTeam(ArrayList<Team> list, Team team) {
        for(Team t : list) {
            if (team.equals(t))
                return t;
        }
        return null;
    }

}