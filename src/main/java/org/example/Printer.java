package org.example;

public final class Printer {

    public static void print(String message, Object... variables) {
        System.out.printf(message, variables);
    }

    public static void error(String error, Object... variables) {
        System.err.printf(error, variables);
    }

}
