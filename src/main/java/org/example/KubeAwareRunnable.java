package org.example;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public abstract class KubeAwareRunnable implements Runnable {

    @Override
    public final void run() {
        Config config = buildConfig(new ConfigBuilder());
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
            go(client);
        } catch (RuntimeException runtime) {
            System.err.println("Error: " + runtime.getMessage());
        } catch (CommandException cE) {
            System.err.println("Command error: " + cE.getMessage());
        }
    }

    protected Config buildConfig(ConfigBuilder configBuilder) {
        return configBuilder.build();
    }

    protected abstract void go(KubernetesClient client) throws CommandException;

}
