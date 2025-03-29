package ir.moke;

import ir.moke.jsysbox.system.JSystem;
import ir.moke.jsysbox.system.MemoryInfo;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphDef;

import java.awt.*;
import java.io.IOException;
import java.time.ZonedDateTime;

public class MainClass {
    private static final String RRD_FILE = "/home/mah454/cpu_load.rrd";
    private static final String GRAPH_FILE = "/home/mah454/cpu_graph.png";

    public static void main(String[] args) throws Exception {
        System.out.println(MainClass.class.getCanonicalName());
        createRRD();

        while (true) {
            updateRRD();
            Thread.sleep(1_000); // Wait 1 seconds
            createGraph();
        }

    }

    // Step 1: Create the RRD file
    private static void createRRD() throws IOException {
        RrdDef rrdDef = new RrdDef(RRD_FILE, 1); // Step time: 1 seconds

        // Data Source: Memory Usage, stored as a GAUGE, with a heartbeat of 10 seconds
        rrdDef.addDatasource("mem_used", DsType.GAUGE, 10, 0, Double.NaN);
        rrdDef.addDatasource("mem_buffer", DsType.GAUGE, 10, 0, Double.NaN);
        rrdDef.addDatasource("mem_cache", DsType.GAUGE, 10, 0, Double.NaN);
        rrdDef.addDatasource("swap_used", DsType.GAUGE, 10, 0, Double.NaN);

        // Daily Archive: Store every 1s for 1 minutes (60 records)
        rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 1, 60);

        // 5 seconds Archive: Store every 5 seconds 1 minutes (5 * 12 = 60 records)
        rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 5, 12);

        try (RrdDb rrdDb = RrdDb.getBuilder().setRrdDef(rrdDef).build()) {
            System.out.println("RRD file created: " + RRD_FILE);
        }
    }

    // Step 2: Update the RRD file every 1 seconds
    private static void updateRRD() throws IOException {
        try (RrdDb rrdDb = RrdDb.getBuilder().setPath(RRD_FILE).build()) {
            Sample sample = rrdDb.createSample();
            sample.setTime(System.currentTimeMillis() / 1000); // Current time in seconds
            MemoryInfo memoryInfo = JSystem.memoryInfo();

            double usedMemoryGB = (memoryInfo.total() - memoryInfo.free() - memoryInfo.buffers() - memoryInfo.cached()) / 1024.0 / 1024.0;
            double bufferGB = memoryInfo.buffers() / 1024.0 / 1024.0;
            double cacheGB = memoryInfo.cached() / 1024.0 / 1024.0;
            double swapGB = (memoryInfo.swapTotal() - memoryInfo.swapFree() - memoryInfo.swapCached()) / 1024.0 / 1024.0;

            sample.setValue("mem_used", usedMemoryGB);
            sample.setValue("mem_buffer", bufferGB);
            sample.setValue("mem_cache", cacheGB);
            sample.setValue("swap_used", swapGB);
            sample.update();
            System.out.printf("Mem: %s   %s    %s%n", memoryInfo.free(), memoryInfo.buffers(), memoryInfo.cached());
        }
    }

    private static void createGraph() throws IOException {
        long startTime = ZonedDateTime.now().minusMinutes(1).toEpochSecond();
        long endTime = ZonedDateTime.now().toEpochSecond();

        RrdGraphDef gDef = new RrdGraphDef(startTime, endTime);
        gDef.setWidth(600);
        gDef.setHeight(300);
        gDef.setFilename(GRAPH_FILE);
        gDef.setTitle("Memory information (Last 1 minutes)");
        gDef.setSignature("Generated by JOS");
        gDef.setVerticalLabel("Memory Information");
        gDef.setMinValue(0);
        gDef.setMaxValue(JSystem.memoryInfo().total() / 1024.0 / 1024.0);
        gDef.setAltAutoscale(false);
        gDef.setAltAutoscaleMax(false);
        gDef.setUnit("GB");
        gDef.setAltYMrtg(true);

        gDef.datasource("mem_used", RRD_FILE, "mem_used", ConsolFun.AVERAGE);
        gDef.area("mem_used", Color.green,"Memory Usage");

        gDef.datasource("mem_buffer", RRD_FILE, "mem_buffer", ConsolFun.AVERAGE);
        gDef.line("mem_buffer", Color.red,"Memory Buffer");

        gDef.datasource("mem_cache", RRD_FILE, "mem_cache", ConsolFun.AVERAGE);
        gDef.line("mem_cache", Color.blue,"Memory Cache");

        gDef.datasource("swap_used", RRD_FILE, "swap_used", ConsolFun.AVERAGE);
        gDef.line("swap_used", Color.black,"Swap Used");

        new RrdGraph(gDef);
    }
}