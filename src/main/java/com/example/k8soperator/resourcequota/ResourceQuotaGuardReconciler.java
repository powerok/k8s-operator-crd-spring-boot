package com.example.k8soperator.resourcequota;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@ApplicationScoped
@ControllerConfiguration(
    name = "resourcequotaguard-reconciler",
    maxReconciliationInterval =
        @MaxReconciliationInterval(interval = 1, timeUnit = TimeUnit.MINUTES))
public class ResourceQuotaGuardReconciler implements Reconciler<ResourceQuotaGuard> {

  private static final Logger LOG = Logger.getLogger(ResourceQuotaGuardReconciler.class.getName());
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final KubernetesClient client;

  public ResourceQuotaGuardReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<ResourceQuotaGuard> reconcile(ResourceQuotaGuard resource, Context<ResourceQuotaGuard> context) {
    var spec = resource.getSpec();
    if (spec == null
        || spec.getTargetNamespace() == null
        || spec.getTargetNamespace().isBlank()
        || spec.getWorkloadLabelKey() == null
        || spec.getWorkloadLabelKey().isBlank()
        || spec.getWorkloadLabelValue() == null
        || spec.getWorkloadLabelValue().isBlank()) {
      return fail(resource, "targetNamespace, workloadLabelKey and workloadLabelValue are required");
    }

    int threshold = spec.getThresholdPercent() != null ? spec.getThresholdPercent() : 80;
    if (threshold <= 0 || threshold > 100) {
      return fail(resource, "thresholdPercent must be between 1 and 100");
    }

    String ns = spec.getTargetNamespace();
    List<ResourceQuota> quotas = client.resourceQuotas().inNamespace(ns).list().getItems();

    double maxRatio = 0.0;
    for (ResourceQuota rq : quotas) {
      if (rq.getStatus() == null || rq.getStatus().getHard() == null || rq.getStatus().getUsed() == null) {
        continue;
      }
      Map<String, Quantity> hard = rq.getStatus().getHard();
      Map<String, Quantity> used = rq.getStatus().getUsed();
      for (Map.Entry<String, Quantity> e : used.entrySet()) {
        Quantity h = hard.get(e.getKey());
        if (h == null) {
          continue;
        }
        double r = ratio(e.getValue(), h);
        maxRatio = Math.max(maxRatio, r);
      }
    }

    var st = Objects.requireNonNullElse(resource.getStatus(), new ResourceQuotaGuardStatus());
    st.setMaxObservedUsageRatio(round4(maxRatio));
    st.setScaledDownDeployments(new ArrayList<>());

    boolean breach = maxRatio * 100.0 >= threshold;
    if (breach) {
      st.setPhase("Alerting");
      st.setMessage(
          String.format(
              "Observed max quota usage ratio %.4f at or above threshold %d%%",
              maxRatio, threshold));
      maybeNotify(spec.getNotificationWebhookUrl(), ns, maxRatio, threshold);

      var deployments =
          client
              .apps()
              .deployments()
              .inNamespace(ns)
              .withLabel(spec.getWorkloadLabelKey(), spec.getWorkloadLabelValue())
              .list()
              .getItems();
      for (var d : deployments) {
        String dn = d.getMetadata().getName();
        Integer rep = d.getSpec() != null ? d.getSpec().getReplicas() : null;
        if (rep != null && rep > 0) {
          client
              .apps()
              .deployments()
              .inNamespace(ns)
              .withName(dn)
              .edit(dep -> new DeploymentBuilder(dep).editSpec().withReplicas(0).endSpec().build());
          st.getScaledDownDeployments().add(dn);
        }
      }
    } else {
      st.setPhase("OK");
      st.setMessage("Quota usage within threshold");
    }

    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }

  private static void maybeNotify(String webhook, String ns, double ratio, int threshold) {
    if (webhook == null || webhook.isBlank()) {
      LOG.warning(() -> String.format("ResourceQuotaGuard: usage %.4f >= %d%% in namespace %s", ratio * 100, threshold, ns));
      return;
    }
    try {
      String body =
          String.format(
              "{\"namespace\":\"%s\",\"maxUsageRatio\":%s,\"thresholdPercent\":%d}",
              ns, Double.toString(round4(ratio)), threshold);
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(webhook))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
      LOG.fine(() -> "Webhook POST status " + res.statusCode());
    } catch (Exception e) {
      LOG.warning("Webhook notification failed: " + e.getMessage());
    }
  }

  private static double ratio(Quantity used, Quantity hard) {
    try {
      BigDecimal u = used.getNumericalAmount();
      BigDecimal h = hard.getNumericalAmount();
      if (h.signum() == 0) {
        return 0;
      }
      return u.divide(h, 8, RoundingMode.HALF_UP).doubleValue();
    } catch (Exception e) {
      return 0;
    }
  }

  private static double round4(double v) {
    return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
  }

  private static UpdateControl<ResourceQuotaGuard> fail(ResourceQuotaGuard resource, String msg) {
    ResourceQuotaGuardStatus st = Objects.requireNonNullElse(resource.getStatus(), new ResourceQuotaGuardStatus());
    st.setPhase("Failed");
    st.setMessage(msg);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }
}
