package com.example.k8soperator.common;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;

public final class OwnerRefs {

  private OwnerRefs() {}

  public static io.fabric8.kubernetes.api.model.OwnerReference controllerRef(HasMetadata cr) {
    return new OwnerReferenceBuilder()
        .withApiVersion(cr.getApiVersion())
        .withKind(cr.getKind())
        .withName(cr.getMetadata().getName())
        .withUid(cr.getMetadata().getUid())
        .withController(true)
        .withBlockOwnerDeletion(true)
        .build();
  }
}
