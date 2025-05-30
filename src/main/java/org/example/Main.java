package org.example;

import org.example.commands.EnableDebug;
import org.example.commands.Namespace;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "kube-play", mixinStandardHelpOptions = true, version = "1.9", description = "Kubernetes helper CLI",
        subcommands = {
                EnableDebug.class,
                Namespace.class
        })
public class Main implements Runnable {

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Run with a subcommand. Use '--help' for options.");
    }

}
