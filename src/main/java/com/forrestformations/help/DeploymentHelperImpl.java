package com.forrestformations.help;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;

final class DeploymentHelperImpl extends KubeAwareHelper implements DeploymentHelper {

    DeploymentHelperImpl(KubernetesClient client) {
        super(client);
    }

    @Override
    public Optional<String> findName(Pod pod) {
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
