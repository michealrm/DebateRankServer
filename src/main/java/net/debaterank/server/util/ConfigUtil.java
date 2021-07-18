package net.debaterank.server.util;

import net.debaterank.server.modules.ModuleManager;
import net.debaterank.server.modules.WorkerPoolManager;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class ConfigUtil {

    private static Configuration config;

    static {
        Configurations configs = new Configurations();
        try {
            config = configs.properties(new File("config.properties"));
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static int getWorkerPoolCount() {
        return config.getInt("worker_pool");
    }

    public static void addModules(ModuleManager moduleManager, WorkerPoolManager workerManager, ArrayList<EntryInfo<EntryInfo.JOTEventLinks>> jotEntries, ArrayList<EntryInfo<EntryInfo.TabroomEventInfo>> tabroomEntries) {
        String[] modules = config.getString("modules").split(",");
        for(String m : modules) {
            if(m.equals("J_LD"))
                moduleManager.newModule(new net.debaterank.server.modules.jot.LD(jotEntries, workerManager.newPool()));
            else if(m.equals("J_PF"))
                moduleManager.newModule(new net.debaterank.server.modules.jot.PF(jotEntries, workerManager.newPool()));
            else if(m.equals("J_CX"))
                moduleManager.newModule(new net.debaterank.server.modules.jot.CX(jotEntries, workerManager.newPool()));
            else if(m.equals("T_LD"))
                moduleManager.newModule(new net.debaterank.server.modules.tabroom.LD(tabroomEntries, workerManager.newPool()));
            else if(m.equals("T_PF"))
                moduleManager.newModule(new net.debaterank.server.modules.tabroom.PF(tabroomEntries, workerManager.newPool()));
            else if(m.equals("T_CX"))
                moduleManager.newModule(new net.debaterank.server.modules.tabroom.CX(tabroomEntries, workerManager.newPool()));
        }
    }

    public static ArrayList<String> getCircuits() {
        // TODO: Want to be able to have choice of inclusive or exclusive
        return new ArrayList<>(Arrays.asList(config.getString("tabroom_circuits").split(",")));
    }


}
