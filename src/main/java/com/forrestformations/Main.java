package com.forrestformations;

import com.forrestformations.commands.Namespace;
import com.forrestformations.commands.PodWatch;
import com.forrestformations.commands.PortForward;
import com.forrestformations.commands.RemoteDebug;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "kube-play", mixinStandardHelpOptions = true, version = "1.9", description = "Kubernetes helper CLI",
        subcommands = {
                RemoteDebug.class,
                Namespace.class,
                PodWatch.class,
                PortForward.class
        })
public class Main implements Runnable {

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        Printer.print("Run with a subcommand. Use '--help' for options.");
    }

}
