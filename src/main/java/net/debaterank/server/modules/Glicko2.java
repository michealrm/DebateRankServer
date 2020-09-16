package net.debaterank.server.modules;

import net.debaterank.server.models.CXRound;
import net.debaterank.server.models.LDRound;
import net.debaterank.server.models.PFRound;
import net.debaterank.server.util.HibernateUtil;
import org.goochjs.glicko2.Rating;
import org.goochjs.glicko2.RatingCalculator;
import org.goochjs.glicko2.RatingPeriodResults;
import org.goochjs.glicko2.Result;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Glicko2 implements Runnable {

    private DebateType type;
    private WorkerPool manager;

    public enum DebateType {
        LD ("LD"),
        PF("PF"),
        CX("CX");

        private String type;

        DebateType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public Glicko2(DebateType type, WorkerPool manager) {
        this.type = type;
        this.manager = manager;
    }

    public void run() {
        manager.newModule(() -> {
            updateRankings();
        });
    }

    private void updateRankings() {
        Session session = HibernateUtil.getSession();
        Transaction transaction = session.beginTransaction();

        List<String> seasons = new ArrayList<>();
        List<Double> seasonsResult = session.createSQLQuery("SELECT DISTINCT EXTRACT(year FROM date) FROM tournament " +
                "ORDER BY EXTRACT(year FROM date)").list();
        for(Double d : seasonsResult)
            seasons.add(String.valueOf(d.intValue()));

        for(String season : seasons) {
            String end = String.valueOf(Integer.parseInt(season) + 1);
            List<Object[]> debates = session.createSQLQuery("SELECT event.id,tournament_id, a_id, n_id, " +
                        "string_agg(b.decision, ','), t.date, round FROM " + type.getType() + "Round event JOIN Tournament AS t ON " +
                    "t.id=event.tournament_id JOIN " + type.getType()+ "Ballot AS b ON event.id=b.round_id WHERE tournament_id " +
                    "IN (SELECT id FROM Tournament WHERE date>='" + season + "-08-01 00:00:00.000' AND date<'" + end +
                    "-08-01 00:00:00.000') AND NOT a_id=n_id AND bye=false AND aAfter=0 GROUP BY t.date,tournament_id," +
                    "round,a_id,n_id,event.id ORDER BY t.date, round")
                    .list();

            HashMap<String, Rating> ratings = new HashMap<>();
            for(Rating r : Rating.getRatings(season, type.getType()))
                ratings.put(r.getUid(), r);

            RatingCalculator ratingSystem = new RatingCalculator(0.05, 0.75);
            RatingPeriodResults results = new RatingPeriodResults();

            Date lastDate = null;
            String lastRound = null;
            for (Object[] d : debates) {
                Date currentDate = (Date)d[5];
                String currentRound = String.valueOf(d[6]);
                // if new period save each results before
                // then calculate new ratings, update ratings, then for each rating assign
                if(lastDate != null && lastRound != null && (!lastDate.equals(currentDate) || !lastRound.equals(currentRound))) {
                    results.saveBeforeRatings();
                    ratingSystem.updateRatings(results);

                    for(Result r : results.getResults()) {
                        Rating aff = null;
                        Rating neg = null;
                        double taBefore = 0;
                        double tnBefore = 0;

                        if(r.isAffWinner()) {
                            aff = r.getWinner();
                            neg = r.getLoser();
                            taBefore = r.getwBefore();
                            tnBefore = r.getlBefore();
                        } else {
                            aff = r.getLoser();
                            neg = r.getWinner();
                            taBefore = r.getlBefore();
                            tnBefore = r.getwBefore();
                        }

                        final double aBefore = taBefore;
                        final double nBefore = tnBefore;
                        double aAfter = aff.getGlicko2Rating();
                        double nAfter = neg.getGlicko2Rating();

                        final Session finalSession = HibernateUtil.getSession();
                        final Transaction finalTransaction = finalSession.beginTransaction();
                        manager.newModule(() -> {
                            if (type == DebateType.LD) {
                                LDRound round = (LDRound) finalSession.createQuery("from LDRound where id = :i")
                                        .setParameter("i", (r.getRoundID()))
                                        .getSingleResult();
                                round.setaBefore(aBefore);
                                round.setnBefore(nBefore);
                                round.setaAfter(aAfter);
                                round.setnAfter(nAfter);
                                finalSession.saveOrUpdate(round);
                            }
                            if (type == DebateType.PF) {
                                PFRound round = (PFRound) finalSession.createQuery("from PFRound where id = :i")
                                        .setParameter("i", (r.getRoundID()))
                                        .getSingleResult();
                                round.setaBefore(aBefore);
                                round.setnBefore(nBefore);
                                round.setaAfter(aAfter);
                                round.setnAfter(nAfter);
                                finalSession.saveOrUpdate(round);
                            }
                            if (type == DebateType.CX) {
                                CXRound round = (CXRound) finalSession.createQuery("from CXRound where id = :i")
                                        .setParameter("i", (r.getRoundID()))
                                        .getSingleResult();
                                round.setaBefore(aBefore);
                                round.setnBefore(nBefore);
                                round.setaAfter(aAfter);
                                round.setnAfter(nAfter);
                                finalSession.saveOrUpdate(round);
                            }
                            finalTransaction.commit();
                            finalSession.close();
                        });
                    }
                }
                lastDate = currentDate;
                lastRound = currentRound;

                Rating aff = null, neg = null;
                aff = ratings.get(String.valueOf(d[2]));
                neg = ratings.get(String.valueOf(d[3]));

                if (aff == null) {
                    aff = new Rating(String.valueOf(d[2]), ratingSystem, season, type.getType());
                    ratings.put(aff.getUid(), aff);
                }
                if (neg == null) {
                    neg = new Rating(String.valueOf(d[3]), ratingSystem, season, type.getType());
                    ratings.put(neg.getUid(), neg);
                }

                int affBallots = 0;
                int totalBallots = 0;
                for (String s : String.valueOf(d[4]).split(",")) {
                    if (s.equals("Aff"))
                        affBallots++;
                    totalBallots++;
                }
                double affWinPercentage = (double) affBallots / totalBallots;

                if (affWinPercentage > .5)
                    results.addResult(aff, neg, true, ((BigInteger)d[0]).intValue());
                else
                    results.addResult(neg, aff, false, ((BigInteger)d[0]).intValue());
            }
            for(Rating r : ratings.values())
                session.saveOrUpdate(r);
        }
        transaction.commit();
        session.close();
    }

}
