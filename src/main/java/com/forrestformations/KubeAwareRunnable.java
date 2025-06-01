package com.forrestformations;

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
            Printer.error("Error: %s", runtime.getMessage());
        } catch (CommandException cE) {
            Printer.error("Command error: %s", cE.getMessage());
        }
    }

    protected Config buildConfig(ConfigBuilder configBuilder) {
        return configBuilder.build();
    }

    protected abstract void go(KubernetesClient client) throws CommandException;

}
