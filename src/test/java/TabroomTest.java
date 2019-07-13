import net.debaterank.server.Server;
import net.debaterank.server.models.Tournament;
import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.WorkerPool;
import net.debaterank.server.modules.WorkerPoolManager;
import net.debaterank.server.modules.tabroom.TabroomEntryScraper;
import net.debaterank.server.util.EntryInfo;
import org.hibernate.jdbc.Work;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class TabroomTest {

    private ModuleManager moduleManager;
    private WorkerPoolManager workerManager;

    @Before
    public void setUp() {
        moduleManager = new ModuleManager();
        workerManager = new WorkerPoolManager();
    }

    /*
    @Test
    public void testCX() {
        Tournament tCEDA = new Tournament("CEDA Nationals Long Beach", "link", "state", new SimpleDateFormat("MM/dd/yyyy").parse("04/04/2019"));
        EntryInfo<EntryInfo.TabroomEventInfo> eiCEDA = new EntryInfo<>();
        ArrayList<Tournament> tQueue = new ArrayList<>();
        tQueue.add(tCEDA);
        ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> entryQueue = new ArrayList<>();
        entryQueue.add(eiCEDA);
        moduleManager.newModule(new TabroomEntryScraper(tQueue, entryQueue, new WorkerPool()));
        Server.execute("event info", workerManager, moduleManager);

        moduleManager.newModule(new net.debaterank.server.modules.tabroom.CX(entryQueue, workerManager.newPool()));

        Session session = HibernateUtil.getSession();
        // TODO: Implement database assertions
    }
    */

    @After
    public void cleanUp() {
        moduleManager.shutdown();
    }

}
