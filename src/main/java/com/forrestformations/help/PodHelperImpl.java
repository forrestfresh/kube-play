package com.forrestformations.help;

import java.util.List;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

final class PodHelperImpl extends KubeAwareHelper implements PodHelper {

    PodHelperImpl(KubernetesClient client) {
        super(client);
    }

    @Override
    public List<Pod> find(Predicate<ObjectMeta> filter) {
        return client.pods()
                .list()
                .getItems()
                .stream()
                .filter(pod -> filter.test(pod.getMetadata()))
                .toList();
    }

}
