package com.forrestformations.commands;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.forrestformations.CommandException;
import com.forrestformations.KubeAwareCommand;
import io.fabric8.kubernetes.api.model.Pod;
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
        terminal.writer().printf("Pods found with regex \"%s\" within \"%s\" namespace:\n\n", regex, client.getNamespace());
        printMatchingPods(client, terminal);
        terminal.writer().printf("\nPress CTRL+C to quit. Updated at %s", LocalTime.now());
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

        String status = (pod.getMetadata().getDeletionTimestamp() == null) ?
                pod.getStatus().getPhase() : "Terminating";

        String age = formatDuration(parseAge(pod));

        String statement = String.format("%-60s %-13s %s", name, status, age);
        terminal.writer().println(statement);
    }

    private Duration parseAge(Pod pod) {
        String timestamp = pod.getMetadata().getCreationTimestamp();
        OffsetDateTime creationTime = OffsetDateTime.parse(timestamp, ISO_OFFSET_DATE_TIME);
        return Duration.between(creationTime, OffsetDateTime.now());
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

}
