package ml140036d;

import ml140036d.simulation.Transaction;
import ml140036d.simulation.server.CurrentSolutionServer;
import ml140036d.simulation.server.Link;
import ml140036d.simulation.server.PushPullPushServer;
import ml140036d.simulation.server.Server;
import ml140036d.util.Constants;
import ml140036d.util.SafePrint;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.awt.geom.Line2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

public class Simulator {

    private enum SimulationStrategy { // da li se simulira trenutno resenje ili PPP protokol
        CURRENT_SOLUTION,
        PUSH_PULL_PUSH
    }

    // funkcija izvrsava jednu simulaciju
    private ArrayList<Server> simulate(int numberOfServers, int numberOfTransactions, SimulationStrategy simulationStrategy) {
        Semaphore toSignal = new Semaphore(-numberOfServers + 1, true);
        int maximumPeerSubsetSize = Constants.PEER_SUBSET_MULTIPLIER * ((int) Math.log(numberOfServers));

        if (maximumPeerSubsetSize <= 0) {
            maximumPeerSubsetSize = 1;
        }
        
        ArrayList<Server> servers = initServers(numberOfServers, simulationStrategy, toSignal, maximumPeerSubsetSize);

        sendTransactions(servers, numberOfTransactions);

        startServers(servers);

        toSignal.acquireUninterruptibly(); // ceka se da svi serveri prime sve transakcije u svoju RAM memoriju

        for (Server server : servers) {
            server.finishPassiveThread();
        }

        return servers;
    }

    // funkcija inicijalizuje servere i formira podskup suseda za svaki server
    private ArrayList<Server> initServers(int numberOfServers, SimulationStrategy simulationStrategy, Semaphore toSignal, int averagePeerSubsetSize) {
        ArrayList<Server> servers = new ArrayList<>(numberOfServers);

        for (int i = 0; i < numberOfServers; i++) {
            Server server = createServer(simulationStrategy, toSignal);
            server.setServerName(Constants.SERVER + "_" + (i + 1));

            if (i > 0) {
                while (true) { // minimalna velicina podskupa suseda je 1
                    int peerIndex = ThreadLocalRandom.current().nextInt(0, servers.size());
                    Server peer = servers.get(peerIndex);

                    if (peer.getPeerSubset().size() == 2 * averagePeerSubsetSize) {
                        continue;
                    }

                    Link.LinkType linkType = ThreadLocalRandom.current().nextInt(0, 101) <= Constants.LAN_LIMIT ? Link.LinkType.LAN : Link.LinkType.WAN;

                    server.addPeer(new Link(linkType, peer));
                    peer.addPeer(new Link(linkType, server));

                    break;
                }
            }

            servers.add(server);
        }

        for (int k = 0; k < servers.size(); k++) { // dalje se formira podskup suseda za sve servere, tako da nikad ne premasi vrednost [ln(n), 2ln(n), 3ln(n)]
            Server server = servers.get(k);
            int peerSubsetSize = ThreadLocalRandom.current().nextInt(1, 2 * averagePeerSubsetSize + 1) - server.getPeerSubset().size();

            if (peerSubsetSize <= 0) {
                continue;
            }

            for (int i = 0; i < peerSubsetSize; i++) {
                int peerIndex = ThreadLocalRandom.current().nextInt(0, servers.size());
                Server peer = servers.get(peerIndex);

                if (server.equals(peer) || server.peerSubsetContains(peer) || peer.getPeerSubset().size() == averagePeerSubsetSize) {
                    i--;
                    continue;
                }

                Link.LinkType linkType = ThreadLocalRandom.current().nextInt(0, 101) <= Constants.LAN_LIMIT ? Link.LinkType.LAN : Link.LinkType.WAN;

                server.addPeer(new Link(linkType, peer));
                peer.addPeer(new Link(linkType, server));
            }

        }

        return servers;
    }

    // funkcija vraca server u zavisnosti od strategije simulacije
    private Server createServer(SimulationStrategy simulationStrategy, Semaphore toSignal) {
        switch (simulationStrategy) {
            case CURRENT_SOLUTION:
                return new CurrentSolutionServer(toSignal);
            case PUSH_PULL_PUSH:
                return new PushPullPushServer(toSignal);
        }

        return null;
    }

    // funkcija simulira slanje klijentskih transakcija nasumicno odabranim serverima
    private void sendTransactions(ArrayList<Server> servers, int numberOfTransactions) {
        for (int i = 0; i < numberOfTransactions; i++) {

            int serverIndex = ThreadLocalRandom.current().nextInt(0, servers.size());
            // link izmedju klijenta i servera je uvek LAN link
            int delay = ThreadLocalRandom.current().nextInt(Constants.MIN_LAN_DELAY, Constants.MAX_LAN_DELAY);

            // inicijalni broj hopova je 0
            Transaction transaction = new Transaction(i, delay, 0);

            servers.get(serverIndex).publish(transaction);

        }
    }

    // pokrecu se aktivna i pasivna nit servera
    private void startServers(ArrayList<Server> servers) {
        for (Server server : servers) {
            server.start();
        }
    }

    // klasa se koristi za generisanje grafika u jfreechart
    private static class Metrics {

        private XYDataset redundancyDataSet;
        private XYDataset delayDataSet;
        private XYDataset hopCountDataSet;

        public Metrics(XYDataset redundancyDataSet, XYDataset delayDataSet, XYDataset hopCountDataSet) {
            this.redundancyDataSet = redundancyDataSet;
            this.delayDataSet = delayDataSet;
            this.hopCountDataSet = hopCountDataSet;
        }

        public XYDataset getRedundancyDataSet() {
            return redundancyDataSet;
        }

        public XYDataset getDelayDataSet() {
            return delayDataSet;
        }

        public XYDataset getHopCountDataSet() {
            return hopCountDataSet;
        }
    }

    public static void main(String args[]) {
        Metrics metrics = createMetrics();

        String redundancyChart_title = "Redundantnost - poređenje aritmetičke sredine [" + Constants.NUM_OF_TRANSACTIONS + "t]";
        String redundancyChart_xAxisLabel = "Broj servera";
        String redundancyChart_yAxisLabel = "Redundantnost - aritmetička sredina [%]";
        JFreeChart redundancyChart = ChartFactory.createXYLineChart(redundancyChart_title, redundancyChart_xAxisLabel, redundancyChart_yAxisLabel, metrics.getRedundancyDataSet());
        customizeChart(redundancyChart);

        String delayChart_title = "Prosečno kašnjenje - poređenje aritmetičke sredine [" + Constants.NUM_OF_TRANSACTIONS + "t]";
        String delayChart_xAxisLabel = "Broj servera";
        String delayChart_yAxisLabel = "Prosečno kašnjenje - aritmetička sredina [s]";
        JFreeChart delayChart = ChartFactory.createXYLineChart(delayChart_title, delayChart_xAxisLabel, delayChart_yAxisLabel, metrics.getDelayDataSet());
        customizeChart(delayChart);

        String hopCountChart_title = "Maksimalni broj hopova - poređenje aritmetičke sredine [" + Constants.NUM_OF_TRANSACTIONS + "t]";
        String hopCountChart_xAxisLabel = "Broj servera";
        String hopCountChart_yAxisLabel = "Maksimalni broj hopova - aritmetička sredina";
        JFreeChart hopCountChart = ChartFactory.createXYLineChart(hopCountChart_title, hopCountChart_xAxisLabel, hopCountChart_yAxisLabel, metrics.getHopCountDataSet());
        customizeChart(hopCountChart);

        try {
            ChartUtilities.saveChartAsJPEG(new File(Constants.DATAGRAM_FILE_PATH + Constants.REDUNDANCY + Constants.JPEG), redundancyChart, Constants.JPEG_WIDTH, Constants.JPEG_HEIGHT);
            ChartUtilities.saveChartAsJPEG(new File(Constants.DATAGRAM_FILE_PATH + Constants.DELAY + Constants.JPEG), delayChart, Constants.JPEG_WIDTH, Constants.JPEG_HEIGHT);
            ChartUtilities.saveChartAsJPEG(new File(Constants.DATAGRAM_FILE_PATH + Constants.HOP_COUNT + Constants.JPEG), hopCountChart, Constants.JPEG_WIDTH, Constants.JPEG_HEIGHT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // funkcija koja vraca informacije potrebne za generisanje grafika - u ovoj funkciji pokrecu se simulacije
    private static Metrics createMetrics() {
        XYSeries currentSolutionRedundancy = new XYSeries("Trenutno rešenje");
        XYSeries pushPullPushRedundancy = new XYSeries("Push pull push");

        XYSeries currentSolutionDelay = new XYSeries("Trenutno rešenje");
        XYSeries pushPullPushDelay = new XYSeries("Push pull push");

        XYSeries currentSolutionHopCount = new XYSeries("Trenutno rešenje");
        XYSeries pushPullPushHopCount = new XYSeries("Push pull push");


        for (int numberOfServers : Constants.SERVER_NUM) {
            // Simulacija za trenutno resenje
            Simulator simulator = new Simulator();
            ArrayList<Server> firstSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.CURRENT_SOLUTION);
            simulator = new Simulator();
            ArrayList<Server> secondSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.CURRENT_SOLUTION);
            simulator = new Simulator();
            ArrayList<Server> thirdSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.CURRENT_SOLUTION);

            double firstRedundancy = 0;
            double firstDelay = 0;
            double firstHopCount = 0;

            double secondRedundancy = 0;
            double secondDelay = 0;
            double secondHopCount = 0;

            double thirdRedundancy = 0;
            double thirdDelay = 0;
            double thirdHopCount = 0;
            for (int i = 0; i < numberOfServers; i++) {
                Server server = firstSimulationServers.get(i);

                firstRedundancy += server.getOverhead();
                firstDelay += (server.getAverageDelay() / 1000);
                firstHopCount += server.getMaxHopCount();

                server = secondSimulationServers.get(i);

                secondRedundancy += server.getOverhead();
                secondDelay += (server.getAverageDelay() / 1000);
                secondHopCount += server.getMaxHopCount();

                server = thirdSimulationServers.get(i);

                thirdRedundancy += server.getOverhead();
                thirdDelay += (server.getAverageDelay() / 1000);
                thirdHopCount += server.getMaxHopCount();
            }
            currentSolutionRedundancy.add(numberOfServers, (firstRedundancy / numberOfServers + secondRedundancy / numberOfServers + thirdRedundancy / numberOfServers) / 3);
            currentSolutionDelay.add(numberOfServers, (firstDelay / numberOfServers + secondDelay / numberOfServers + thirdDelay / numberOfServers) / 3);
            currentSolutionHopCount.add(numberOfServers, (firstHopCount / numberOfServers + secondHopCount / numberOfServers + thirdHopCount / numberOfServers) / 3);

            // Simulacija za PPP protokol
            simulator = new Simulator();
            firstSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.PUSH_PULL_PUSH);
            simulator = new Simulator();
            secondSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.PUSH_PULL_PUSH);
            simulator = new Simulator();
            thirdSimulationServers = simulator.simulate(numberOfServers, Constants.NUM_OF_TRANSACTIONS, SimulationStrategy.PUSH_PULL_PUSH);

            firstRedundancy = 0;
            firstDelay = 0;
            firstHopCount = 0;

            secondRedundancy = 0;
            secondDelay = 0;
            secondHopCount = 0;

            thirdRedundancy = 0;
            thirdDelay = 0;
            thirdHopCount = 0;
            for (int i = 0; i < numberOfServers; i++) {
                Server server = firstSimulationServers.get(i);

                firstRedundancy += server.getOverhead();
                firstDelay += (server.getAverageDelay() / 1000);
                firstHopCount += server.getMaxHopCount();

                server = secondSimulationServers.get(i);

                secondRedundancy += server.getOverhead();
                secondDelay += (server.getAverageDelay() / 1000);
                secondHopCount += server.getMaxHopCount();

                server = thirdSimulationServers.get(i);

                thirdRedundancy += server.getOverhead();
                thirdDelay += (server.getAverageDelay() / 1000);
                thirdHopCount += server.getMaxHopCount();
            }

            pushPullPushRedundancy.add(numberOfServers, (firstRedundancy / numberOfServers + secondRedundancy / numberOfServers + thirdRedundancy / numberOfServers) / 3);
            pushPullPushDelay.add(numberOfServers, (firstDelay / numberOfServers + secondDelay / numberOfServers + thirdDelay / numberOfServers) / 3);
            pushPullPushHopCount.add(numberOfServers, (firstHopCount / numberOfServers + secondHopCount / numberOfServers + thirdHopCount / numberOfServers) / 3);
        }

        XYSeriesCollection redundancyDataSet = new XYSeriesCollection();
        redundancyDataSet.addSeries(currentSolutionRedundancy);
        redundancyDataSet.addSeries(pushPullPushRedundancy);

        XYSeriesCollection delayDataSet = new XYSeriesCollection();
        delayDataSet.addSeries(currentSolutionDelay);
        delayDataSet.addSeries(pushPullPushDelay);

        XYSeriesCollection hopCountDataSet = new XYSeriesCollection();
        hopCountDataSet.addSeries(currentSolutionHopCount);
        hopCountDataSet.addSeries(pushPullPushHopCount);

        Metrics metrics = new Metrics(redundancyDataSet, delayDataSet, hopCountDataSet);
        return metrics;
    }

    // funkcija koja postavlja parametre izgleda grafika
    private static void customizeChart(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);

        renderer.setLegendLine(new Line2D.Double(-50.0D, 0.0D, 50.0D, 0.0D));

        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesPaint(1, Color.GREEN);

        renderer.setSeriesStroke(0, new BasicStroke(
                5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                5.0f, new float[]{20.0f, 20.0f}, 2.0f
        ));
        renderer.setSeriesStroke(1, new BasicStroke(5.0f));

        plot.setRenderer(renderer);

        plot.setBackgroundPaint(Color.WHITE);

        Font labelFont = new Font("AxisLabelFont", Font.BOLD, 30);
        plot.getDomainAxis().setLabelFont(labelFont);
        plot.getRangeAxis().setLabelFont(labelFont);

        Font axisValuesFont = new Font("AxisValuesFont", Font.PLAIN, 25);
        plot.getDomainAxis().setTickLabelFont(axisValuesFont);
        plot.getRangeAxis().setTickLabelFont(axisValuesFont);

        Font legendTitleFont = new Font("AxisLabelFont", Font.BOLD, 30);
        chart.getLegend().setItemFont(legendTitleFont);
        chart.getTitle().setFont(legendTitleFont);
    }

}
