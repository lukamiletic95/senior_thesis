package ml140036d.simulation.server;

import ml140036d.simulation.Transaction;
import ml140036d.util.Constants;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public abstract class Server {

    private class ActiveThread extends Thread {

        @Override
        public void run() { // Aktivna nit periodicno diseminira informacije
            while (!activeThreadFinished) {
                try {
                    Thread.sleep(Constants.GOSSIP_PERIOD);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (activeThreadFinished) {
                    return;
                }

                gossip();
            }
        }

    }

    private class PassiveThread extends Thread {

        @Override
        public void run() {
            while (true) {
                requestsEmpty.acquireUninterruptibly();

                if (passiveThreadFinished) {
                    return;
                }

                requestBufferSemaphore.acquireUninterruptibly();
                Object request = requestBuffer.remove(0);
                requestBufferSemaphore.release();

                processRequest(request);

                if (mempool.size() == Constants.NUM_OF_TRANSACTIONS && !activeThreadFinished) {
                    // diseminacija po poslednji put, da bi se prosledile novodobijene informacije
                    // sve sto se primi od sad na dalje bice redundantno
                    gossip();

                    activeThreadFinished = true;
                    toSignal.release();
                }
            }
        }
    }

    protected boolean activeThreadFinished = false;
    protected boolean passiveThreadFinished = false;

    protected ArrayList<Transaction> mempool = new ArrayList<>();

    protected int numOfRedundantTransactionsReceived = 0;

    protected long sumDelay = 0;

    protected ArrayList<Link> peerSubset = new ArrayList<>();

    protected String serverName;

    private ActiveThread activeThread = new ActiveThread();
    private PassiveThread passiveThread = new PassiveThread();

    private Semaphore toSignal;

    private ArrayList<Object> requestBuffer = new ArrayList<>();
    private Semaphore requestBufferSemaphore = new Semaphore(1, true);
    private Semaphore requestsEmpty = new Semaphore(0, true);

    public Server(Semaphore toSignal) {
        this.toSignal = toSignal;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void start() {
        activeThread.start();
        passiveThread.start();
    }

    public void addPeer(Link link) {
        peerSubset.add(link);
    }

    public abstract void gossip();

    public abstract void processRequest(Object request);

    // kada klijent posalje svoju transakciju
    public abstract void publish(Transaction transaction);

    protected void send(Object request) {
        requestBufferSemaphore.acquireUninterruptibly();
        requestBuffer.add(request);
        requestBufferSemaphore.release();

        requestsEmpty.release();
    }

    public ArrayList<Link> getPeerSubset() {
        return peerSubset;
    }

    public double getOverhead() {
        if (this instanceof CurrentSolutionServer) {
            return (numOfRedundantTransactionsReceived * 100f) / (numOfRedundantTransactionsReceived + mempool.size());
        } else {
            int divisor = Constants.AVERAGE_TRANSACTION_SIZE_IN_BYTES / Constants.IDENTIFIER_SIZE_IN_BYTES;
            return (((double) numOfRedundantTransactionsReceived / divisor) * 100f) / (((double) numOfRedundantTransactionsReceived / divisor) + mempool.size());
        }
    }

    public double getAverageDelay() {
        return ((float) sumDelay) / mempool.size();
    }

    public int getMaxHopCount() {
        int maxHopCount = 0;

        for (Transaction t : mempool) {
            if (t.getHopCount() > maxHopCount) {
                maxHopCount = t.getHopCount();
            }
        }

        return maxHopCount;
    }

    public void finishPassiveThread() {
        passiveThreadFinished = true;
        requestsEmpty.release();
    }
}
