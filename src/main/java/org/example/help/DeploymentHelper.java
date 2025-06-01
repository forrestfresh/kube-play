package org.example.help;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.Pod;

public interface DeploymentHelper {

    Optional<String> findName(Pod pod);

}
