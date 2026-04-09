package com.example.k8soperator.resourcequota;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceQuotaGuardSpec {

  /** Namespace whose ResourceQuota objects are evaluated. */
  private String targetNamespace;

  /** Alert / scale threshold in percent (0–100). Default 80. */
  private Integer thresholdPercent;

  /** Label key to select Deployments to scale down when over threshold. */
  private String workloadLabelKey;

  /** Label value paired with workloadLabelKey. */
  private String workloadLabelValue;

  /** Optional URL to POST a short JSON alert (best-effort). */
  private String notificationWebhookUrl;

  public String getTargetNamespace() {
    return targetNamespace;
  }

  public void setTargetNamespace(String targetNamespace) {
    this.targetNamespace = targetNamespace;
  }

  public Integer getThresholdPercent() {
    return thresholdPercent;
  }

  public void setThresholdPercent(Integer thresholdPercent) {
    this.thresholdPercent = thresholdPercent;
  }

  public String getWorkloadLabelKey() {
    return workloadLabelKey;
  }

  public void setWorkloadLabelKey(String workloadLabelKey) {
    this.workloadLabelKey = workloadLabelKey;
  }

  public String getWorkloadLabelValue() {
    return workloadLabelValue;
  }

  public void setWorkloadLabelValue(String workloadLabelValue) {
    this.workloadLabelValue = workloadLabelValue;
  }

  public String getNotificationWebhookUrl() {
    return notificationWebhookUrl;
  }

  public void setNotificationWebhookUrl(String notificationWebhookUrl) {
    this.notificationWebhookUrl = notificationWebhookUrl;
  }
}
