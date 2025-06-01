package com.forrestformations.commands;

import io.fabric8.kubernetes.client.KubernetesClient;
import com.forrestformations.KubeAwareCommand;
import com.forrestformations.Printer;
import picocli.CommandLine.Command;

@Command(name = "namespace", description = "Print namespace")
public final class Namespace extends KubeAwareCommand {

    @Override
    public void go(KubernetesClient client) {
        Printer.print(client.getNamespace());
    }

}
