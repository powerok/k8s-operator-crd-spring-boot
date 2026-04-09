package com.example.k8soperator.staticsite;

import com.example.k8soperator.common.OwnerRefs;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
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
import java.util.Objects;

@ApplicationScoped
@ControllerConfiguration(name = "staticsitedeployer-reconciler")
public class StaticSiteDeployerReconciler implements Reconciler<StaticSiteDeployer> {

  private static final String DEFAULT_NGINX = "nginx:1.25-alpine";
  private static final String GIT_IMAGE = "alpine/git:latest";

  private final KubernetesClient client;

  public StaticSiteDeployerReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<StaticSiteDeployer> reconcile(StaticSiteDeployer resource, Context<StaticSiteDeployer> context) {
    var spec = resource.getSpec();
    if (spec == null || spec.getGitRepoUrl() == null || spec.getGitRepoUrl().isBlank()) {
      return fail(resource, "gitRepoUrl is required");
    }
    String branch = spec.getBranch() != null && !spec.getBranch().isBlank() ? spec.getBranch() : "main";
    String nginxImage = spec.getNginxImage() != null && !spec.getNginxImage().isBlank() ? spec.getNginxImage() : DEFAULT_NGINX;

    var meta = resource.getMetadata();
    String ns = meta.getNamespace();
    String name = meta.getName();
    String base = "staticsite-" + name;
    var owner = OwnerRefs.controllerRef(resource);

    String cloneScript =
        "set -eux; rm -rf /site/*; git clone --depth 1 --branch \"${GIT_BRANCH}\" \"${GIT_REPO}\" /site";

    var deployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName(base)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .addToLabels("app.kubernetes.io/name", "staticsitedeployer")
            .addToLabels("app.kubernetes.io/instance", name)
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withNewSelector()
            .addToMatchLabels("app.kubernetes.io/name", "staticsitedeployer")
            .addToMatchLabels("app.kubernetes.io/instance", name)
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app.kubernetes.io/name", "staticsitedeployer")
            .addToLabels("app.kubernetes.io/instance", name)
            .endMetadata()
            .withSpec(
                new PodSpecBuilder()
                    .addNewInitContainer()
                    .withName("git-sync")
                    .withImage(GIT_IMAGE)
                    .withCommand("sh", "-c", cloneScript)
                    .addToEnv(
                        new EnvVarBuilder().withName("GIT_REPO").withValue(spec.getGitRepoUrl()).build(),
                        new EnvVarBuilder().withName("GIT_BRANCH").withValue(branch).build())
                    .addToVolumeMounts(
                        new VolumeMountBuilder().withName("html").withMountPath("/site").build())
                    .endInitContainer()
                    .addNewContainer()
                    .withName("nginx")
                    .withImage(nginxImage)
                    .addToPorts(new ContainerPortBuilder().withContainerPort(80).withName("http").build())
                    .addToVolumeMounts(
                        new VolumeMountBuilder().withName("html").withMountPath("/usr/share/nginx/html").build())
                    .endContainer()
                    .addToVolumes(
                        new VolumeBuilder()
                            .withName("html")
                            .withEmptyDir(new EmptyDirVolumeSourceBuilder().build())
                            .build())
                    .build())
            .endTemplate()
            .endSpec()
            .build();

    client.resource(deployment).serverSideApply();

    var service =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(base)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .addToLabels("app.kubernetes.io/name", "staticsitedeployer")
            .endMetadata()
            .withNewSpec()
            .addToSelector("app.kubernetes.io/name", "staticsitedeployer")
            .addToSelector("app.kubernetes.io/instance", name)
            .addToPorts(new ServicePortBuilder().withName("http").withPort(80).withTargetPort(new IntOrString(80)).build())
            .endSpec()
            .build();

    client.resource(service).serverSideApply();

    StaticSiteDeployerStatus st = Objects.requireNonNullElse(resource.getStatus(), new StaticSiteDeployerStatus());
    st.setPhase("Ready");
    st.setMessage("Nginx + git clone scheduled; service " + base);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }

  private static UpdateControl<StaticSiteDeployer> fail(StaticSiteDeployer resource, String msg) {
    StaticSiteDeployerStatus st = Objects.requireNonNullElse(resource.getStatus(), new StaticSiteDeployerStatus());
    st.setPhase("Failed");
    st.setMessage(msg);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }
}
