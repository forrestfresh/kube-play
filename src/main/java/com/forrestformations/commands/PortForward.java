package com.forrestformations.commands;

import static java.util.stream.Collectors.toList;
import com.forrestformations.KubeAwareCommand;
import com.forrestformations.KubeCtlException;
import com.forrestformations.Printer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
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

        portForward(pods);
    }

    private void portForward(List<Pod> pods) {
        try {
            Printer.print("Enabling port forward for:");

            IntStream.range(0, pods.size())
                    .forEach(idx -> portForwardWithKubeCtl(pods.get(idx), port + idx));

            Printer.print("Port forwarding started. Press Ctrl+C to stop.");
            Thread.currentThread().join();
        } catch (InterruptedException iE) {
            Printer.error("Port forward error: %s", iE.getMessage());
        }
    }

    private void portForwardWithKubeCtl(Pod pod, int localPort) {
        Printer.print("%d:%d - %s", remotePort, localPort, pod.getMetadata().getName());
        try {
            String ns = pod.getMetadata().getNamespace();
            Printer.print("Forwarding pod in namespace %s", ns);
            Process process = startKubectlPortForward(pod, localPort, remotePort);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process.isAlive()) {
                    process.destroy();
                }
            }));
        } catch (Exception e) {
            throw new KubeCtlException(String.format("Failed to port forward pod %s", pod.getMetadata().getName()), e);
        }
    }

    private Process startKubectlPortForward(Pod pod, int localPort, int remotePort) {
        String namespace = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();

        List<String> command = List.of(
                "kubectl", "port-forward",
                "pod/" + podName,
                localPort + ":" + remotePort,
                "-n", namespace
        );

        try {
            Printer.print("Starting kubectl port-forward: %s", String.join(" ", command));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO(); // optional: pipe to stdout/stderr
            return builder.start(); // returns immediately
        } catch (IOException e) {
            throw new KubeCtlException(String.format("Failed to port forward pod %s", pod.getMetadata().getName()), e);
        }
    }

}
