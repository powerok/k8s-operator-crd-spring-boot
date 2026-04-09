package com.example.k8soperator.maintenance;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@ControllerConfiguration(
    name = "maintenancewindow-reconciler",
    maxReconciliationInterval =
        @MaxReconciliationInterval(interval = 1, timeUnit = TimeUnit.MINUTES))
public class MaintenanceWindowReconciler implements Reconciler<MaintenanceWindow> {

  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

  private final KubernetesClient client;

  public MaintenanceWindowReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<MaintenanceWindow> reconcile(MaintenanceWindow resource, Context<MaintenanceWindow> context) {
    var spec = resource.getSpec();
    if (spec == null
        || spec.getTargetNamespace() == null
        || spec.getTargetNamespace().isBlank()
        || spec.getDeploymentName() == null
        || spec.getDeploymentName().isBlank()
        || spec.getWindowStart() == null
        || spec.getWindowStart().isBlank()
        || spec.getWindowEnd() == null
        || spec.getWindowEnd().isBlank()) {
      return fail(resource, "targetNamespace, deploymentName, windowStart, windowEnd are required");
    }

    String zoneId = spec.getTimezone() != null && !spec.getTimezone().isBlank() ? spec.getTimezone() : "UTC";
    ZoneId zone;
    try {
      zone = ZoneId.of(zoneId);
    } catch (Exception e) {
      return fail(resource, "Invalid timezone: " + zoneId);
    }

    LocalTime start;
    LocalTime end;
    try {
      start = LocalTime.parse(spec.getWindowStart().trim(), HM);
      end = LocalTime.parse(spec.getWindowEnd().trim(), HM);
    } catch (Exception e) {
      return fail(resource, "windowStart/windowEnd must be HH:mm");
    }

    int inRep = spec.getReplicasDuringWindow() != null ? spec.getReplicasDuringWindow() : 1;
    int outRep = spec.getReplicasOutsideWindow() != null ? spec.getReplicasOutsideWindow() : 0;

    LocalTime now = LocalTime.now(zone);
    boolean inside = withinWindow(now, start, end);

    String ns = spec.getTargetNamespace();
    String depName = spec.getDeploymentName();

    Deployment current = client.apps().deployments().inNamespace(ns).withName(depName).get();
    if (current == null) {
      return fail(resource, "Deployment not found: " + ns + "/" + depName);
    }

    int desired = inside ? inRep : outRep;
    Integer cur = current.getSpec() != null ? current.getSpec().getReplicas() : null;
    if (cur == null || cur != desired) {
      Deployment patched =
          new DeploymentBuilder(current).editSpec().withReplicas(desired).endSpec().build();
      client.resource(patched).update();
    }

    var st = Objects.requireNonNullElse(resource.getStatus(), new MaintenanceWindowStatus());
    st.setPhase("Ready");
    st.setWithinWindow(inside);
    st.setAppliedReplicas(desired);
    st.setMessage(
        String.format(
            "Deployment %s/%s scaled to %d (%s window)",
            ns, depName, desired, inside ? "inside" : "outside"));
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }

  /** Inclusive start, exclusive end on one calendar day; supports overnight when start is after end. */
  public static boolean withinWindow(LocalTime now, LocalTime start, LocalTime end) {
    if (!start.isAfter(end)) {
      return !now.isBefore(start) && now.isBefore(end);
    }
    return !now.isBefore(start) || now.isBefore(end);
  }

  private static UpdateControl<MaintenanceWindow> fail(MaintenanceWindow resource, String msg) {
    MaintenanceWindowStatus st = Objects.requireNonNullElse(resource.getStatus(), new MaintenanceWindowStatus());
    st.setPhase("Failed");
    st.setMessage(msg);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }
}
