package ml140036d.simulation.server;

import ml140036d.simulation.Transaction;
import ml140036d.util.Constants;
import ml140036d.util.SafePrint;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

public class PushPullPushServer extends Server {

    private ArrayList<Integer> toPropose = new ArrayList<>();
    private Semaphore toProposeMutex = new Semaphore(1, true);

    private ArrayList<Integer> requested = new ArrayList<>();

    public enum MessageType {
        PUBLISH,
        PROPOSE,
        REQUEST,
        SERVE
    }

    public PushPullPushServer(Semaphore toSignal) {
        super(toSignal);
    }

    @Override
    public void gossip() {
        toProposeMutex.acquireUninterruptibly();

        if (toPropose.size() > 0) {
            for (Link peer : peerSubset) {
                ArrayList<Object> request = new ArrayList<>();

                request.add(toPropose);
                request.add(MessageType.PROPOSE);
                request.add(this);
                request.add(peer.getLinkType());

                peer.getPeer().send(request);
            }

            toPropose = new ArrayList<>(); // Infect and die
        }

        toProposeMutex.release();
    }

    @Override
    public void processRequest(Object request) {
        MessageType messageType = (MessageType) ((ArrayList<Object>) request).get(1);

        switch (messageType) {
            case PUBLISH:
                Transaction transaction = (Transaction) ((ArrayList<Object>) request).get(0);

                sumDelay += transaction.getDelay();

                requested.add(transaction.getId());

                transaction.setHopCount(transaction.getHopCount() + 1);
                mempool.add(transaction);

                toProposeMutex.acquireUninterruptibly();
                toPropose.add(transaction.getId());
                toProposeMutex.release();

                break;
            case PROPOSE:
                ArrayList<Integer> wanted = new ArrayList<>();
                ArrayList<Integer> proposed = (ArrayList<Integer>) ((ArrayList<Object>) request).get(0);

                for (Integer id : proposed) {
                    if (!requested.contains(id)) {
                        wanted.add(id);
                        requested.add(id);
                    } else {
                        numOfRedundantTransactionsReceived++;
                    }
                }

                Server proposeSender = (Server) ((ArrayList<Object>) request).get(2);
                ArrayList<Object> proposeRequest = new ArrayList<>();

                proposeRequest.add(wanted);
                proposeRequest.add(MessageType.REQUEST);
                proposeRequest.add(PushPullPushServer.this);
                proposeRequest.add(((ArrayList<Object>) request).get(3));

                proposeSender.send(proposeRequest);

                break;
            case REQUEST:
                ArrayList<Transaction> asked = new ArrayList<>();
                ArrayList<Integer> requestedIds = (ArrayList<Integer>) ((ArrayList<Object>) request).get(0);

                Link.LinkType linkType = (Link.LinkType) ((ArrayList<Object>) request).get(3);

                for (Integer id : requestedIds) {
                    Transaction t = getEvent(id, linkType);
                    asked.add(t);
                }

                Server requestSender = (Server) ((ArrayList<Object>) request).get(2);
                ArrayList<Object> requestRequest = new ArrayList<>();

                requestRequest.add(asked);
                requestRequest.add(MessageType.SERVE);

                requestSender.send(requestRequest);

                break;
            case SERVE:
                ArrayList<Transaction> events = (ArrayList<Transaction>) ((ArrayList<Object>) request).get(0);

                for (Transaction t : events) {
                    sumDelay += t.getDelay();

                    t.setHopCount(t.getHopCount() + 1);
                    mempool.add(t);

                    toProposeMutex.acquireUninterruptibly();
                    toPropose.add(t.getId());
                    toProposeMutex.release();
                }

                break;
        }
    }

    private Transaction getEvent(int id, Link.LinkType linkType) {
        for (Transaction transaction : mempool) {
            if (transaction.getId() == id) {
                int divisor = Constants.AVERAGE_TRANSACTION_SIZE_IN_BYTES / Constants.IDENTIFIER_SIZE_IN_BYTES;

                int proposeDelay = linkType.equals(Link.LinkType.WAN) ? ThreadLocalRandom.current().nextInt(Constants.MIN_WAN_DELAY, Constants.MAX_WAN_DELAY) : ThreadLocalRandom.current().nextInt(Constants.MIN_LAN_DELAY, Constants.MAX_LAN_DELAY);
                proposeDelay /= divisor;

                int requestDelay = linkType.equals(Link.LinkType.WAN) ? ThreadLocalRandom.current().nextInt(Constants.MIN_WAN_DELAY, Constants.MAX_WAN_DELAY) : ThreadLocalRandom.current().nextInt(Constants.MIN_LAN_DELAY, Constants.MAX_LAN_DELAY);
                requestDelay /= divisor;

                int serveDelay = linkType.equals(Link.LinkType.WAN) ? ThreadLocalRandom.current().nextInt(Constants.MIN_WAN_DELAY, Constants.MAX_WAN_DELAY) : ThreadLocalRandom.current().nextInt(Constants.MIN_LAN_DELAY, Constants.MAX_LAN_DELAY);

                return new Transaction(transaction.getId(), transaction.getDelay() + proposeDelay + requestDelay + serveDelay, transaction.getHopCount());
            }
        }

        return null;
    }

    @Override
    public void publish(Transaction transaction) {
        ArrayList<Object> request = new ArrayList<>();

        request.add(transaction);
        request.add(MessageType.PUBLISH);

        send(request);
    }

}
