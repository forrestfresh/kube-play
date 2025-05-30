package org.example.commands;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.example.KubeAwareRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "enable-debug", description = "Enables java debug for the selected pods")
public final class EnableDebug extends KubeAwareRunnable {

    private static final Logger logger = LoggerFactory.getLogger(Namespace.class);

    private static final Set<String> DEBUG_VARS = Set.of("CATALINA_OPTS");
    private static final String DEBUG_STATEMENT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000";

    @Parameters(index = "0", description = "pod-name.*")
    private String regex = ".*";

    @Option(names = {"-s", "--suspend"}, description = "Whether debug should suspend the application")
    private boolean suspend = false;

    @Override
    public void go(KubernetesClient client) {
        List<Pod> pods = client.pods()
                .list()
                .getItems()
                .stream()
                .filter(pod -> pod.getMetadata().getName().matches(regex))
                .toList();

        logger.info("Pods found:");
        Set<String> deploymentNames = pods.stream()
                .peek(pod -> logger.info(pod.getMetadata().getName()))
                .map(pod -> findDeployments(pod, client))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toSet());

        logger.info("Corresponding deployments:");
        List<Deployment> deployments = deploymentNames.stream()
                .map(name -> client.apps()
                        .deployments()
                        .withName(name)
                        .get())
                .peek(deployment -> logger.info(deployment.getMetadata().getName()))
                .toList();

        deployments.forEach(deployment -> edit(deployment, client));
    }

    private void edit(Deployment deployment, KubernetesClient client) {
        List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();

        AtomicBoolean updated = new AtomicBoolean(false);
        containers.forEach(container -> updated.compareAndSet(false, editContainer(container)));

        if (!updated.get()) {
            return;
        }

        logger.info("Saving deployment {}", deployment.getMetadata().getName());
        client.apps().deployments()
                .withName(deployment.getMetadata().getName())
                .edit(d -> deployment);
    }

    private boolean editContainer(Container container) {
        List<EnvVar> envVars = container.getEnv();

        if (envVars == null) {
            return false;
        }

        Optional<EnvVar> catalinaEnvVar = envVars.stream()
                .filter(envVar -> DEBUG_VARS.contains(envVar.getName()))
                .filter(envVar -> !envVar.getValue().contains(DEBUG_STATEMENT))
                .findFirst();

        catalinaEnvVar.ifPresent(envVar -> envVar.setValue(envVar.getValue() + " " + DEBUG_STATEMENT));

        boolean hasDebugPort = container.getPorts().stream()
                .anyMatch(port -> port.getContainerPort() == 8000);

        if (!hasDebugPort) {
            ContainerPort containerPort = new ContainerPortBuilder()
                    .withName("debug")
                    .withProtocol("TCP")
                    .withContainerPort(8000)
                    .build();
            container.getPorts().add(containerPort);
        }

        return catalinaEnvVar.isPresent() || !hasDebugPort;
    }

    private Optional<String> findDeployments(Pod pod, KubernetesClient client) {
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

}
