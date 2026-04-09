package com.example.k8soperator.apigateway;

import com.example.k8soperator.common.OwnerRefs;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@ControllerConfiguration(name = "apigatewayroute-reconciler")
public class ApiGatewayRouteReconciler implements Reconciler<ApiGatewayRoute> {

  private final KubernetesClient client;

  public ApiGatewayRouteReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<ApiGatewayRoute> reconcile(ApiGatewayRoute resource, Context<ApiGatewayRoute> context) {
    var spec = resource.getSpec();
    if (spec == null
        || spec.getHost() == null
        || spec.getHost().isBlank()
        || spec.getBackendServiceName() == null
        || spec.getBackendServiceName().isBlank()
        || spec.getBackendPort() == null
        || spec.getBackendPort() <= 0) {
      return fail(resource, "host, backendServiceName and backendPort are required");
    }

    String path = spec.getPathPrefix() != null && !spec.getPathPrefix().isBlank() ? spec.getPathPrefix() : "/";
    if (!path.startsWith("/")) {
      path = "/" + path;
    }

    var meta = resource.getMetadata();
    String ns = meta.getNamespace();
    String name = meta.getName();
    var owner = OwnerRefs.controllerRef(resource);

    Map<String, String> ann = new HashMap<>();
    if (Boolean.TRUE.equals(spec.getRequireAuth())) {
      ann.put("nginx.ingress.kubernetes.io/auth-type", "basic");
      if (spec.getAuthSecretName() != null && !spec.getAuthSecretName().isBlank()) {
        ann.put("nginx.ingress.kubernetes.io/auth-secret", spec.getAuthSecretName());
      } else {
        ann.put("nginx.ingress.kubernetes.io/auth-snippet", "return 401;");
      }
    }

    var pathRule =
        new HTTPIngressPathBuilder()
            .withPath(path)
            .withPathType("Prefix")
            .withBackend(
                new IngressBackendBuilder()
                    .withNewService()
                    .withName(spec.getBackendServiceName())
                    .withPort(new ServiceBackendPortBuilder().withNumber(spec.getBackendPort()).build())
                    .endService()
                    .build())
            .build();

    if (spec.getMergeIntoIngress() != null && !spec.getMergeIntoIngress().isBlank()) {
      Ingress existing = client.network().v1().ingresses().inNamespace(ns).withName(spec.getMergeIntoIngress()).get();
      if (existing == null) {
        return fail(resource, "Ingress not found: " + spec.getMergeIntoIngress());
      }
      List<IngressRule> rules =
          existing.getSpec() != null && existing.getSpec().getRules() != null
              ? new ArrayList<>(existing.getSpec().getRules())
              : new ArrayList<>();

      boolean merged = false;
      for (int i = 0; i < rules.size(); i++) {
        IngressRule r = rules.get(i);
        if (spec.getHost().equals(r.getHost())) {
          List<HTTPIngressPath> paths =
              r.getHttp() != null && r.getHttp().getPaths() != null
                  ? new ArrayList<>(r.getHttp().getPaths())
                  : new ArrayList<>();
          paths.add(pathRule);
          IngressRule nr = new IngressRuleBuilder(r).withHttp(ruleHttp(paths)).build();
          rules.set(i, nr);
          merged = true;
          break;
        }
      }
      if (!merged) {
        rules.add(
            new IngressRuleBuilder()
                .withHost(spec.getHost())
                .withHttp(ruleHttp(List.of(pathRule)))
                .build());
      }

      Ingress patched = new IngressBuilder(existing).editSpec().withRules(rules).endSpec().build();
      client.resource(patched).update();

      var st = Objects.requireNonNullElse(resource.getStatus(), new ApiGatewayRouteStatus());
      st.setPhase("Ready");
      st.setIngressName(spec.getMergeIntoIngress());
      st.setMessage("Merged rule into existing Ingress");
      resource.setStatus(st);
      return UpdateControl.patchStatus(resource);
    }

    String ingressName = "apigateway-" + name;
    var ingress =
        new IngressBuilder()
            .withNewMetadata()
            .withName(ingressName)
            .withNamespace(ns)
            .withOwnerReferences(owner)
            .addToLabels("app.kubernetes.io/name", "apigatewayroute")
            .addToAnnotations(ann)
            .endMetadata()
            .withNewSpec()
            .withIngressClassName(spec.getIngressClassName())
            .withRules(
                new IngressRuleBuilder()
                    .withHost(spec.getHost())
                    .withHttp(ruleHttp(List.of(pathRule)))
                    .build())
            .endSpec()
            .build();

    client.resource(ingress).serverSideApply();

    var st2 = Objects.requireNonNullElse(resource.getStatus(), new ApiGatewayRouteStatus());
    st2.setPhase("Ready");
    st2.setIngressName(ingressName);
    st2.setMessage("Ingress created");
    resource.setStatus(st2);
    return UpdateControl.patchStatus(resource);
  }

  private static HTTPIngressRuleValue ruleHttp(List<HTTPIngressPath> paths) {
    return new HTTPIngressRuleValueBuilder().withPaths(paths).build();
  }

  private static UpdateControl<ApiGatewayRoute> fail(ApiGatewayRoute resource, String msg) {
    ApiGatewayRouteStatus st = Objects.requireNonNullElse(resource.getStatus(), new ApiGatewayRouteStatus());
    st.setPhase("Failed");
    st.setMessage(msg);
    resource.setStatus(st);
    return UpdateControl.patchStatus(resource);
  }
}
