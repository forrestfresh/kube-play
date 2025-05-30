package org.example.commands;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.example.KubeAwareRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

@Command(name = "namespace", description = "Print namespace")
public final class Namespace extends KubeAwareRunnable {

    private static final Logger logger = LoggerFactory.getLogger(Namespace.class);

    @Override
    public void go(KubernetesClient client) {
        logger.info(client.getNamespace());
    }

}
