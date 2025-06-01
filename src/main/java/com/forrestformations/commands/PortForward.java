package com.forrestformations.commands;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.IntStream;

import com.forrestformations.KubeAwareCommand;
import com.forrestformations.Printer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "port-forward", description = "Print namespace")
public final class PortForward extends KubeAwareCommand {

    @Parameters(index = "0", description = "pod-name.*")
    private String regex = ".*";

    @Option(names = {"-p", "--port"}, description = "Local port number to start from; default 8001")
    private int port = 8001;

    @Option(names = {"-r", "--remotePort"}, description = "Remote port number; default 8000")
    private int remotePort = 8000;

    @Override
    public void go(KubernetesClient client) {
        List<Pod> pods = client.pods()
                .list()
                .getItems()
                .stream()
                .filter(pod -> pod.getMetadata()
                        .getName()
                        .matches(regex))
                .toList();

        if (pods.isEmpty()) {
            Printer.print("No matching pods using the regex \"%s\"", regex);
            return;
        }

        portForward(pods, client);
    }

    private void portForward(List<Pod> pods, KubernetesClient client) {
        try {
            Printer.print("Enabling port forward for:");

            List<LocalPortForward> portForwards = IntStream.range(0, pods.size())
                    .mapToObj(idx -> portForward(pods.get(idx), port + idx, client))
                    .collect(toList());

            portForwards.forEach(f -> System.out.println(f.getLocalAddress()));

            Printer.print("Port forwarding started. Press Ctrl+C to stop.");
            Thread.currentThread().join();
        } catch (InterruptedException iE) {
            Printer.error("Port forward error: %s", iE.getMessage());
        }
    }

    private LocalPortForward portForward(Pod pod, int localPort, KubernetesClient client) {
        Printer.print("%d:%d - %s", remotePort, localPort, pod.getMetadata().getName());
        return client.pods()
                .withName(pod.getMetadata().getName())
                .portForward(remotePort, localPort);
    }

}
