package com.example.k8soperator.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaintenanceWindowSpec {

  private String targetNamespace;
  private String deploymentName;

  /** IANA zone id, e.g. Asia/Seoul */
  private String timezone;

  /** Local window start, HH:mm */
  private String windowStart;

  /** Local window end, HH:mm */
  private String windowEnd;

  private Integer replicasDuringWindow;
  private Integer replicasOutsideWindow;

  public String getTargetNamespace() {
    return targetNamespace;
  }

  public void setTargetNamespace(String targetNamespace) {
    this.targetNamespace = targetNamespace;
  }

  public String getDeploymentName() {
    return deploymentName;
  }

  public void setDeploymentName(String deploymentName) {
    this.deploymentName = deploymentName;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getWindowStart() {
    return windowStart;
  }

  public void setWindowStart(String windowStart) {
    this.windowStart = windowStart;
  }

  public String getWindowEnd() {
    return windowEnd;
  }

  public void setWindowEnd(String windowEnd) {
    this.windowEnd = windowEnd;
  }

  public Integer getReplicasDuringWindow() {
    return replicasDuringWindow;
  }

  public void setReplicasDuringWindow(Integer replicasDuringWindow) {
    this.replicasDuringWindow = replicasDuringWindow;
  }

  public Integer getReplicasOutsideWindow() {
    return replicasOutsideWindow;
  }

  public void setReplicasOutsideWindow(Integer replicasOutsideWindow) {
    this.replicasOutsideWindow = replicasOutsideWindow;
  }
}
