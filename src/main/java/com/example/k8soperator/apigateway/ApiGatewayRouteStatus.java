package com.example.k8soperator.apigateway;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiGatewayRouteStatus {

  private String phase;
  private String message;
  private String ingressName;

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

  public String getIngressName() {
    return ingressName;
  }

  public void setIngressName(String ingressName) {
    this.ingressName = ingressName;
  }
}
