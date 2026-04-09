package com.example.k8soperator.ghostdatabase;

import com.example.k8soperator.common.OwnerRefs;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@ControllerConfiguration(name = "ghostdatabase-reconciler")
public class GhostDatabaseReconciler implements Reconciler<GhostDatabase> {

  private static final String DEFAULT_IMAGE = "postgres:16-alpine";

  private final KubernetesClient client;

  public GhostDatabaseReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<GhostDatabase> reconcile(GhostDatabase resource, Context<GhostDatabase> context) {
    var meta = resource.getMetadata();
    var spec = resource.getSpec();
    if (spec == null || spec.getDatabaseName() == null || spec.getDatabaseName().isBlank()) {
      return fail(resource, "databaseName is required");
    }
    if (spec.getStorageSize() == null || spec.getStorageSize().isBlank()) {
      return fail(resource, "storageSize is required");
    }

    String ns = meta.getNamespace();
    String name = meta.getName();
    String base = "ghostdb-" + name;
    String pvcName = base + "-pvc";
    String secretName = base + "-secret";
    String deployName = base;
    String svcName = base;

    var owner = OwnerRefs.controllerRef(resource);

    if (client.secrets().inNamespace(ns).withName(secretName).get() == null) {
      client
          .secrets()
          .inNamespace(ns)
          .resource(
              new SecretBuilder()
                  .withNewMetadata()
                  .withName(secretName)
                  .withNamespace(ns)
                  .withOwnerReferences(owner)
                  .endMetadata()
                  .withType("Opaque")
                  .withStringData(
                      Map.of(
                          "POSTGRES_USER", spec.getDatabaseName(),
                          "POSTGRES_PASSWORD", randomPassword(),
                          "POSTGRES_DB", spec.getDatabaseName()))
                  .build())
          .create();
    }

    String image = spec.getPostgresImage() != null && !spec.getPostgresImage().isBlank()
        ? spec.getPostgresImage()
        : DEFAULT_IMAGE;

    var pvc =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(pvcName)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .withRequests(Map.of("storage", new Quantity(spec.getStorageSize())))
            .endResources()
            .endSpec()
            .build();

    client.persistentVolumeClaims().inNamespace(ns).resource(pvc).serverSideApply();

    var deployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName(deployName)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .addToLabels("app.kubernetes.io/name", "ghostdatabase")
            .addToLabels("app.kubernetes.io/instance", name)
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withNewSelector()
            .addToMatchLabels("app.kubernetes.io/name", "ghostdatabase")
            .addToMatchLabels("app.kubernetes.io/instance", name)
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app.kubernetes.io/name", "ghostdatabase")
            .addToLabels("app.kubernetes.io/instance", name)
            .endMetadata()
            .withSpec(
                new PodSpecBuilder()
                    .addNewContainer()
                    .withName("postgres")
                    .withImage(image)
                    .addToPorts(new ContainerPortBuilder().withContainerPort(5432).withName("pg").build())
                    .addToEnv(
                        envFromSecret("POSTGRES_USER", secretName, "POSTGRES_USER"),
                        envFromSecret("POSTGRES_PASSWORD", secretName, "POSTGRES_PASSWORD"),
                        envFromSecret("POSTGRES_DB", secretName, "POSTGRES_DB"))
                    .addToVolumeMounts(
                        new VolumeMountBuilder().withName("data").withMountPath("/var/lib/postgresql/data").build())
                    .endContainer()
                    .addToVolumes(
                        new VolumeBuilder()
                            .withName("data")
                            .withPersistentVolumeClaim(
                                new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(pvcName).build())
                            .build())
                    .build())
            .endTemplate()
            .endSpec()
            .build();

    if (spec.getInitSql() != null && !spec.getInitSql().isBlank()) {
      String cmName = base + "-init";
      client
          .configMaps()
          .inNamespace(ns)
          .resource(
              new io.fabric8.kubernetes.api.model.ConfigMapBuilder()
                  .withNewMetadata()
                  .withName(cmName)
                  .withNamespace(ns)
                  .withOwnerReferences(owner)
                  .endMetadata()
                  .addToData("init.sql", spec.getInitSql())
                  .build())
          .serverSideApply();

      deployment =
          new DeploymentBuilder(deployment)
              .editSpec()
              .editTemplate()
              .editSpec()
              .editContainer(0)
              .addToVolumeMounts(
                  new VolumeMountBuilder().withName("init").withMountPath("/docker-entrypoint-initdb.d").withReadOnly(true).build())
              .endContainer()
              .addToVolumes(
                  new VolumeBuilder()
                      .withName("init")
                      .withNewConfigMap()
                          .withName(cmName)
                          .withOptional(false)
                      .endConfigMap()
                      .build())
              .endSpec()
              .endTemplate()
              .endSpec()
              .build();
    }

    client.resource(deployment).serverSideApply();

    var service =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(svcName)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .addToLabels("app.kubernetes.io/name", "ghostdatabase")
            .endMetadata()
            .withNewSpec()
            .addToSelector("app.kubernetes.io/name", "ghostdatabase")
            .addToSelector("app.kubernetes.io/instance", name)
            .addToPorts(
                new ServicePortBuilder()
                    .withName("pg")
                    .withPort(5432)
                    .withTargetPort(new IntOrString(5432))
                    .build())
            .endSpec()
            .build();

    client.resource(service).serverSideApply();

    GhostDatabaseStatus st = Objects.requireNonNullElse(resource.getStatus(), new GhostDatabaseStatus());
    st.setPhase("Ready");
    st.setMessage("PostgreSQL deployed");
    st.setServiceName(svcName);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }

  private static UpdateControl<GhostDatabase> fail(GhostDatabase resource, String msg) {
    GhostDatabaseStatus st = Objects.requireNonNullElse(resource.getStatus(), new GhostDatabaseStatus());
    st.setPhase("Failed");
    st.setMessage(msg);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }

  private static io.fabric8.kubernetes.api.model.EnvVar envFromSecret(String envName, String secret, String key) {
    return new EnvVarBuilder()
        .withName(envName)
        .withNewValueFrom()
        .withNewSecretKeyRef()
        .withName(secret)
        .withKey(key)
        .withOptional(false)
        .endSecretKeyRef()
        .endValueFrom()
        .build();
  }

  private static String randomPassword() {
    byte[] buf = new byte[24];
    new SecureRandom().nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }
}
