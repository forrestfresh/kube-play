package com.forrestformations.help;

import io.fabric8.kubernetes.client.KubernetesClient;

abstract class KubeAwareHelper {

    protected final KubernetesClient client;

    KubeAwareHelper(KubernetesClient client) {
        this.client = client;
    }

}
