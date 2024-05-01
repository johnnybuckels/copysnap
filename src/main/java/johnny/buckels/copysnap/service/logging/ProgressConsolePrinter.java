package johnny.buckels.copysnap.service.logging;

public class ProgressConsolePrinter {

    private static final String FORMAT = "\r%s [%s%s] %d/%d \u001B[33m%2.0f%%\u001B[0m \u001B[33m\u001B[0m";
//            .formatted("Progress:", "█".repeat(i), "-".repeat(100 - i), 1/100 * (float)100, "Complete");

    private final String prefix;

    public ProgressConsolePrinter(String prefix) {
        this.prefix = prefix;
    }

    public void write(int completed, int total) {
        double percentage = (double)completed/total * 100;
        System.out.printf(FORMAT, prefix, "#".repeat((int) percentage), "-".repeat(100 - (int) percentage), completed, total, percentage);
    }

}
