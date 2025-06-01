package com.forrestformations;

public final class Printer {

    public static void print(String message, Object... variables) {
        System.out.printf(message + '\n', variables);
    }

    public static void error(String error, Object... variables) {
        System.err.printf(error + '\n', variables);
    }

}
