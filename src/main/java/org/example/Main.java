package org.example;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
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
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class Main {

    private static final Set<String> DEBUG_VARS = Set.of("CATALINA_OPTS");
    private static final String DEBUG_STATEMENT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000";

    public static void main(String[] args) {
        try (KubernetesClient client = new KubernetesClientBuilder().build(); Scanner scanner = new Scanner(System.in)) {
            System.out.println("Namespace: " + client.getNamespace());

            System.out.println("Provide regex to identify pods to edit: ");
            String regex = scanner.nextLine().trim();

            List<Pod> pods = client.pods()
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> pod.getMetadata().getName().matches(regex))
                    .toList();

            Set<String> deploymentNames = pods.stream()
                    .peek(pod -> System.out.println(pod.getMetadata().getName()))
                    .map(pod -> findDeployments(pod, client))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toSet());

            List<Deployment> deployments = deploymentNames.stream()
                    .peek(System.out::println)
                    .map(name -> client.apps()
                            .deployments()
                            .withName(name)
                            .get())
                    .toList();

            deployments.stream()
                    .peek(deployment -> System.out.println(deployment.getMetadata().getName()))
                    .forEach(deployment -> edit(deployment, client));
        }
    }


    private static void edit(Deployment deployment, KubernetesClient client) {
        List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();

        AtomicBoolean updated = new AtomicBoolean(false);
        containers.forEach(container -> updated.compareAndSet(false, editContainer(container)));

        if (!updated.get()) {
            return;
        }

        System.out.println("Saving deployment " + deployment.getMetadata().getName());
        client.apps().deployments()
                .withName(deployment.getMetadata().getName())
                .edit(d -> deployment);
    }

    private static boolean editContainer(Container container) {
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

    private static Optional<String> findDeployments(Pod pod, KubernetesClient client) {
        Optional<OwnerReference> replicaSetOwnerRef = pod.getMetadata().getOwnerReferences().stream()
                .filter(owner -> "ReplicaSet".equals(owner.getKind()))
                .findFirst();

        if (replicaSetOwnerRef.isEmpty()) {
            System.out.println("No ReplicaSet owner found for this Pod");
            return Optional.empty();
        }

        String replicaSetName = replicaSetOwnerRef.get().getName();

        ReplicaSet replicaSet = client.apps().replicaSets()
                .withName(replicaSetName)
                .get();

        if (replicaSet == null) {
            System.out.println("ReplicaSet not found: " + replicaSetName);
            return Optional.empty();
        }

        Optional<OwnerReference> deploymentOwnerRef = replicaSet.getMetadata().getOwnerReferences().stream()
                .filter(owner -> "Deployment".equals(owner.getKind()))
                .findFirst();

        if (deploymentOwnerRef.isEmpty()) {
            System.out.println("No Deployment owner found for ReplicaSet: " + replicaSetName);
            return Optional.empty();
        }

        return Optional.of(deploymentOwnerRef.get().getName());
    }

}
