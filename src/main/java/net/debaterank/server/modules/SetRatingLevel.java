package net.debaterank.server.modules;

import net.debaterank.server.models.CXRound;
import net.debaterank.server.models.LDRound;
import net.debaterank.server.models.PFRound;
import net.debaterank.server.util.HibernateUtil;
import org.goochjs.glicko2.Rating;
import org.goochjs.glicko2.RatingCalculator;
import org.goochjs.glicko2.RatingPeriodResults;
import org.goochjs.glicko2.Result;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SetRatingLevel implements Runnable {

    private WorkerPool manager;

    public SetRatingLevel(WorkerPool manager) {
        this.manager = manager;
    }

    public void run() {
        manager.newModule(() -> {
            updateLDandPF();
            updateCX();
        });
    }

    private void updateLDandPF() {
        Session session = HibernateUtil.getSession();
        Transaction transaction = session.beginTransaction();
        session.createNativeQuery("update rating set level='HS' where event='LD' or event='PF'")
                .executeUpdate();
        transaction.commit();
        session.close();
    }

    private void updateCX() {
        Session session = HibernateUtil.getSession();
        Transaction transaction = session.beginTransaction();
        // update rating set level
        session.createNativeQuery("update rating set level='College' where uid in " +
                "(select uid from rating r join cxround as cx on cx.a_id=(cast r.uid as int) or cx.n_id=(cast r.uid as int) " +
                "join tournament as t on cx.tournament_id=t.id where t.circuit='NDTCEDA' order by t.date desc limit by 1)")
                .executeUpdate();
        session.createNativeQuery("update rating set level='HS' where uid in " +
                "(select uid from rating r join cxround as cx on cx.a_id=(cast r.uid as int) or cx.n_id=(cast r.uid as int) " +
                "join tournament as t on cx.tournament_id=t.id where t.circuit='JOT' or t.circuit='NatCir' order by t.date desc limit by 1)")
                .executeUpdate();
        transaction.commit();
        session.close();
    }

}
