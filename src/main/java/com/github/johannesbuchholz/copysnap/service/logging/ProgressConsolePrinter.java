package com.github.johannesbuchholz.copysnap.service.logging;

import java.io.PrintStream;

public class ProgressConsolePrinter {

    private static final PrintStream OUT = System.out;

    private static String computeFormatString(int completed, int total) {
        int maxDigitCount = Math.max(1, (int) Math.ceil(Math.log10(Integer.max(completed, total))));
        return "\r%s [%s%s] %" + maxDigitCount + "d/%d (%3.0f%%)";
    }

    private final String prefix;

    public ProgressConsolePrinter(String prefix) {
        this.prefix = prefix;
    }

    /**
     * PREFIX [#########################---------------------------------------------------------------------------]  64/250 ( 26%)
     */
    public void update(int completed, int total) {
        double percentage = (double)completed/total * 100;
        OUT.printf(computeFormatString(completed, total), prefix, "#".repeat((int) percentage), "-".repeat(100 - (int) percentage), completed, total, percentage);
    }

    public void newLine() {
        OUT.println();
    }
}
