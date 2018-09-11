package ml140036d.simulation;

public class Transaction implements Cloneable {

    private int id;
    private int delay;
    private int hopCount;

    public Transaction(int id, int delay, int hopCount) {
        this.id = id;
        this.delay = delay;
        this.hopCount = hopCount;
    }

    public int getId() {
        return id;
    }

    public int getDelay() {
        return delay;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    @Override
    public Transaction clone() {
        return new Transaction(this.id, this.delay, this.hopCount);
    }

}
