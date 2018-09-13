package ml140036d.util;


// usluzna klasa koja cuva konstante
public final class Constants {

    public static final int AVERAGE_TRANSACTION_SIZE_IN_BYTES = 550;
    public static final int IDENTIFIER_SIZE_IN_BYTES = 32;

    public static final int MIN_LAN_DELAY = 10;
    public static final int MAX_LAN_DELAY = 31;
    public static final int MIN_WAN_DELAY = 100;
    public static final int MAX_WAN_DELAY = 201;

    public static final int LAN_LIMIT = 20;

    public static final long GOSSIP_PERIOD = 10;

    public static final int NUM_OF_TRANSACTIONS = 200;


    public static final int SERVER_NUM[] = {100, 200, 300, 1000};
    public static final int PEER_SUBSET_MULTIPLIER = 1; // {1, 2, 3} -> [ln(n), 2ln(n), 3ln(n)]

    public static final String SERVER = "S";
    public static final String LN = "ln(n)";

    public static final String DATAGRAM_FILE_PATH = "charts/";
    public static final String JPEG = ".JPEG";
    public static final int JPEG_WIDTH = 1400;
    public static final int JPEG_HEIGHT = 800;

    public static final String REDUNDANCY = "Redundancy_" + PEER_SUBSET_MULTIPLIER + LN;
    public static final String DELAY = "Delay_" + PEER_SUBSET_MULTIPLIER + LN;
    public static final String HOP_COUNT = "HopCount_" + PEER_SUBSET_MULTIPLIER + LN;

}
