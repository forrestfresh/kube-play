package com.forrestformations.commands;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.forrestformations.KubeAwareCommand;
import com.forrestformations.Printer;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "remote-debug", description = "Enables/disables java remote debug session for the matching pods")
public final class RemoteDebug extends KubeAwareCommand {

    private static final String DEBUG_STATEMENT = "-agentlib:jdwp=transport=dt_socket," +
            "server=y,suspend=n,address=0.0.0.0:8000";
    private static final Pattern DEBUG_PATTERN = Pattern.compile("-agentlib:jdwp=[^\\s]*");
    private static final String REDUNDANT_WHITESPACE = "\\s{2,}";

    private static final Set<String> DEBUG_VARS = Set.of("CATALINA_OPTS");

    @Parameters(index = "0", description = "pod-name.*")
    private String regex = ".*";

    @Option(names = {"-r", "--remove"}, description = "Remove debug configuration")
    private boolean remove = false;

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
            Printer.print("No matching pods using the regex \"%s\" within \"%s\" namespace",
                    regex, client.getNamespace());
            return;
        }

        Printer.print("Pods found within \"%s\" namespace:", client.getNamespace());
        Set<String> deploymentNames = pods.stream()
                .peek(pod -> Printer.print(pod.getMetadata().getName()))
                .map(pod -> identifyPodDeployment(pod, client))
                .flatMap(Optional::stream)
                .collect(toSet());

        if (deploymentNames.isEmpty()) {
            Printer.print("\nNo corresponding deployments identified");
            return;
        }

        Printer.print("\nIdentified corresponding deployments:");
        List<Deployment> deployments = deploymentNames.stream()
                .map(name -> client.apps()
                        .deployments()
                        .withName(name)
                        .get())
                .peek(deployment -> Printer.print(deployment.getMetadata().getName()))
                .toList();

        List<Deployment> modifiedDeployments = deployments.stream()
                .map(deployment -> modifyDebug(deployment, client))
                .flatMap(Optional::stream)
                .toList();

        if (modifiedDeployments.isEmpty()) {
            Printer.print("\nNo deployments needed modifications");
            return;
        }

        Printer.print("\nSaving modified deployments (remote debug %s):", (remove ? "disabled" : "enabled"));
        modifiedDeployments.stream()
                .peek(deployment -> Printer.print(deployment.getMetadata().getName()))
                .forEach(deployment -> client.apps()
                        .deployments()
                        .withName(deployment.getMetadata().getName())
                        .edit(d -> deployment));
    }

    private Optional<String> identifyPodDeployment(Pod pod, KubernetesClient client) {
        Optional<OwnerReference> replicaSetOwnerRef = pod.getMetadata().getOwnerReferences().stream()
                .filter(owner -> "ReplicaSet".equals(owner.getKind()))
                .findFirst();

        if (replicaSetOwnerRef.isEmpty()) {
            return Optional.empty();
        }

        String replicaSetName = replicaSetOwnerRef.get().getName();
        ReplicaSet replicaSet = client.apps().replicaSets()
                .withName(replicaSetName)
                .get();

        if (replicaSet == null) {
            return Optional.empty();
        }

        return replicaSet.getMetadata().getOwnerReferences().stream()
                .filter(owner -> "Deployment".equals(owner.getKind()))
                .map(OwnerReference::getName)
                .findFirst();
    }

    private Optional<Deployment> modifyDebug(Deployment deployment, KubernetesClient client) {
        List<Container> containers = deployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers();

        boolean updated = containers.stream()
                .map(this::checkAndModifyContainer)
                .reduce(false, (a, b) -> a || b);

        return updated ? Optional.of(deployment) : Optional.empty();
    }

    private boolean checkAndModifyContainer(Container container) {
        List<EnvVar> envVars = container.getEnv();

        if (envVars == null) {
            return false;
        }

        Optional<EnvVar> catalinaEnvVar = envVars.stream()
                .filter(envVar -> DEBUG_VARS.contains(envVar.getName()))
                .findFirst();

        if (catalinaEnvVar.isEmpty()) {
            return false;
        }

        boolean envVarModified = catalinaEnvVar.map(this::modifyEnvVar)
                .orElse(false);
        boolean portModified = modifyPorts(container.getPorts());

        return envVarModified || portModified;
    }

    private boolean modifyEnvVar(EnvVar envVar) {
        String variableValue = envVar.getValue();
        Matcher matcher = DEBUG_PATTERN.matcher(variableValue);

        if (remove && matcher.find()) {
            String updatedValue = matcher.replaceAll("")
                    .replaceAll(REDUNDANT_WHITESPACE, " ")
                    .trim();
            envVar.setValue(updatedValue);
            return true;
        }

        if (!remove && !matcher.find()) {
            envVar.setValue((variableValue + " " + DEBUG_STATEMENT).trim());
            return true;
        }

        return false;
    }

    private boolean modifyPorts(List<ContainerPort> ports) {
        Optional<ContainerPort> debugPort = ports.stream()
                .filter(port -> port.getContainerPort() == 8000)
                .findFirst();

        if (remove && debugPort.isPresent()) {
            ports.remove(debugPort.get());
            return true;
        }

        if (!remove && debugPort.isEmpty()) {
            ports.add(new ContainerPortBuilder()
                    .withName("debug")
                    .withProtocol("TCP")
                    .withContainerPort(8000)
                    .build());
            return true;
        }

        return false;
    }

}