package com.forrestformations.commands;

import static java.lang.String.format;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Optional;

import com.forrestformations.CommandException;
import com.forrestformations.KubeAwareCommand;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "pod-watch", description = "Watches a set of identified pods")
public final class PodWatch extends KubeAwareCommand {

    @Parameters(index = "0", description = "pod-name.*")
    private String regex = ".*";

    @Option(names = {"-i", "--interval"}, description = "How often to update, lowest is 1 second")
    private int interval = 1;

    @Override
    protected void go(KubernetesClient client) throws CommandException {
        try (Terminal terminal = TerminalBuilder.terminal()) {
            render(terminal, client);
        } catch (InterruptedException | IOException e) {
            throw new CommandException("Command exception", e);
        }
    }

    private void render(Terminal terminal, KubernetesClient client) throws InterruptedException, IOException {
        while (true) {
            printScreen(terminal, client);
            Thread.sleep(interval * 1000L);
        }
    }

    private void printScreen(Terminal terminal, KubernetesClient client) {
        terminal.puts(Capability.clear_screen);
        printMatchingPods(client, terminal);
        terminal.writer().println("\nPress CTRL+C to quit. Updated at: " + LocalTime.now());
        terminal.flush();
    }

    private void printMatchingPods(KubernetesClient client, Terminal terminal) {
        client.pods()
                .list()
                .getItems()
                .stream()
                .filter(pod -> pod.getMetadata()
                        .getName()
                        .matches(regex))
                .forEach(pod -> printPod(pod, terminal));
    }

    private void printPod(Pod pod, Terminal terminal) {
        String name = pod.getMetadata()
                .getName();
        String status = Optional.of(pod.getStatus())
                .map(PodStatus::getPhase)
                .orElse("Unknown");
        String statement = format("%-45s %-15s", name, status);
        terminal.writer().println(statement);
    }

}
