package com.example.k8soperator.apigateway;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiGatewayRouteSpec {

  private String host;
  private String pathPrefix;
  private String backendServiceName;
  private Integer backendPort;
  private String ingressClassName;

  /**
   * If set, append rule to this existing Ingress instead of creating a dedicated one.
   */
  private String mergeIntoIngress;

  private Boolean requireAuth;
  /** Secret in the same namespace holding htpasswd for nginx basic auth. */
  private String authSecretName;

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getPathPrefix() {
    return pathPrefix;
  }

  public void setPathPrefix(String pathPrefix) {
    this.pathPrefix = pathPrefix;
  }

  public String getBackendServiceName() {
    return backendServiceName;
  }

  public void setBackendServiceName(String backendServiceName) {
    this.backendServiceName = backendServiceName;
  }

  public Integer getBackendPort() {
    return backendPort;
  }

  public void setBackendPort(Integer backendPort) {
    this.backendPort = backendPort;
  }

  public String getIngressClassName() {
    return ingressClassName;
  }

  public void setIngressClassName(String ingressClassName) {
    this.ingressClassName = ingressClassName;
  }

  public String getMergeIntoIngress() {
    return mergeIntoIngress;
  }

  public void setMergeIntoIngress(String mergeIntoIngress) {
    this.mergeIntoIngress = mergeIntoIngress;
  }

  public Boolean getRequireAuth() {
    return requireAuth;
  }

  public void setRequireAuth(Boolean requireAuth) {
    this.requireAuth = requireAuth;
  }

  public String getAuthSecretName() {
    return authSecretName;
  }

  public void setAuthSecretName(String authSecretName) {
    this.authSecretName = authSecretName;
  }
}
