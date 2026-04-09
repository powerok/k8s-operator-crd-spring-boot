package com.example.k8soperator.resourcequota;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceQuotaGuardStatus {

  private String phase;
  private String message;
  private Double maxObservedUsageRatio;
  private List<String> scaledDownDeployments;

  public String getPhase() {
    return phase;
  }

  public void setPhase(String phase) {
    this.phase = phase;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Double getMaxObservedUsageRatio() {
    return maxObservedUsageRatio;
  }

  public void setMaxObservedUsageRatio(Double maxObservedUsageRatio) {
    this.maxObservedUsageRatio = maxObservedUsageRatio;
  }

  public List<String> getScaledDownDeployments() {
    return scaledDownDeployments;
  }

  public void setScaledDownDeployments(List<String> scaledDownDeployments) {
    this.scaledDownDeployments = scaledDownDeployments;
  }
}
