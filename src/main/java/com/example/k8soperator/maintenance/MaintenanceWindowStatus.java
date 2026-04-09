package com.example.k8soperator.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaintenanceWindowStatus {

  private String phase;
  private String message;
  private Integer appliedReplicas;
  private Boolean withinWindow;

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

  public Integer getAppliedReplicas() {
    return appliedReplicas;
  }

  public void setAppliedReplicas(Integer appliedReplicas) {
    this.appliedReplicas = appliedReplicas;
  }

  public Boolean getWithinWindow() {
    return withinWindow;
  }

  public void setWithinWindow(Boolean withinWindow) {
    this.withinWindow = withinWindow;
  }
}
