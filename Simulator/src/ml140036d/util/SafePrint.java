package ml140036d.util;

public final class SafePrint {

    // koristi se kada je potrebno ispisivanje od strane vise razlicitih niti na standardni izlaz
    public static void safePrintln(String text) {
        synchronized (System.out) {
            System.out.println(text);
        }
    }

}
