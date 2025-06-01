package org.example.help;

import java.util.List;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;

public interface PodHelper {

    List<Pod> find(Predicate<ObjectMeta> filter);

}
