package ml140036d.simulation.server;

import ml140036d.simulation.Transaction;
import ml140036d.util.Constants;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

public class CurrentSolutionServer extends Server {

    private ArrayList<Transaction> toGossip = new ArrayList<>();
    private Semaphore toGossipMutex = new Semaphore(1, true);

    public CurrentSolutionServer(Semaphore toSignal) {
        super(toSignal);
    }

    @Override
    public void gossip() {
        toGossipMutex.acquireUninterruptibly();

        for (int i = 0; i < toGossip.size() && !activeThreadFinished; i++) {
            for (Link peer : peerSubset) {
                Transaction t = toGossip.get(i).clone();

                int delay = peer.getLinkType().equals(Link.LinkType.WAN) ? ThreadLocalRandom.current().nextInt(Constants.MIN_WAN_DELAY, Constants.MAX_WAN_DELAY) : ThreadLocalRandom.current().nextInt(Constants.MIN_LAN_DELAY, Constants.MAX_LAN_DELAY);
                t.setDelay(t.getDelay() + delay);

                peer.getPeer().send(t);
            }
        }

        toGossip.clear(); // Infect and die

        toGossipMutex.release();
    }

    @Override
    public void processRequest(Object request) {
        Transaction transaction = (Transaction) request;

        boolean isInMyMempol = checkMempool(transaction);

        if (isInMyMempol) {
            numOfRedundantTransactionsReceived++;

            return;
        }

        sumDelay += transaction.getDelay();

        transaction.setHopCount(transaction.getHopCount() + 1);

        add(transaction, mempool, null);
        add(transaction, toGossip, toGossipMutex);
    }

    @Override
    public void publish(Transaction transaction) {
        send(transaction);
    }

    private boolean checkMempool(Transaction transaction) {
        boolean isInMyMempool = false;

        for (Transaction t : mempool) {
            if (t.getId() == transaction.getId()) {
                isInMyMempool = true;
                break;
            }
        }

        return isInMyMempool;
    }

    private void add(Transaction t, ArrayList<Transaction> buffer, Semaphore mutex) {
        Transaction transaction = t.clone();

        if (mutex != null) {
            mutex.acquireUninterruptibly();
        }

        buffer.add(transaction);

        if (mutex != null) {
            mutex.release();
        }
    }

}
