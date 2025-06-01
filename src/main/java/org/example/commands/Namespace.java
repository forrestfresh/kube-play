package org.example.commands;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.example.KubeAwareRunnable;
import org.example.Printer;
import picocli.CommandLine.Command;

@Command(name = "namespace", description = "Print namespace")
public final class Namespace extends KubeAwareRunnable {

    @Override
    public void go(KubernetesClient client) {
        Printer.print(client.getNamespace());
    }

}
