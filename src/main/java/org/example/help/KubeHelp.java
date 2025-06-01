package org.example.help;

import io.fabric8.kubernetes.client.KubernetesClient;

public final class KubeHelp {

    public static Branches given(KubernetesClient client) {
        return new Branches(client);
    }

    public final static class Branches extends KubeAwareHelper {

        Branches(KubernetesClient client) {
            super(client);
        }

        public PodHelper pods() {
            return new PodHelperImpl(client);
        }

        public DeploymentHelper deployments() {
            return new DeploymentHelperImpl(client);
        }

    }

}
