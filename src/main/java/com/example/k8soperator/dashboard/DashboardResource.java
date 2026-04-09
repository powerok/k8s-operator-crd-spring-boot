package com.example.k8soperator.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

  private static final String GROUP = "operator.example.com";
  private static final String VERSION = "v1alpha1";

  private final KubernetesClient client;
  private final ObjectMapper objectMapper;

  @Inject
  public DashboardResource(KubernetesClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public record CrdKind(String plural, String kind, String description) {}

  public record ResourceRow(
      String name, String namespace, String phase, String creationTimestamp, String uid) {}

  @GET
  @Path("/health")
  public Map<String, Object> health() {
    Map<String, Object> m = new LinkedHashMap<>();
    try {
      var ver = client.getKubernetesVersion();
      m.put("ok", true);
      m.put("major", ver.getMajor());
      m.put("minor", ver.getMinor());
      m.put("gitVersion", ver.getGitVersion());
      m.put("masterUrl", String.valueOf(client.getMasterUrl()));
    } catch (Exception e) {
      m.put("ok", false);
      m.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
    }
    return m;
  }

  @GET
  @Path("/kinds")
  public List<CrdKind> kinds() {
    return List.of(
        new CrdKind("ghostdatabases", "GhostDatabase", "PostgreSQL · PVC · Service"),
        new CrdKind("staticsitedeployers", "StaticSiteDeployer", "Git clone · Nginx"),
        new CrdKind("resourcequotaguards", "ResourceQuotaGuard", "Quota 임계 · 스케일"),
        new CrdKind("maintenancewindows", "MaintenanceWindow", "업무 시간 Replica"),
        new CrdKind("apigatewayroutes", "ApiGatewayRoute", "Ingress 라우트"));
  }

  @GET
  @Path("/namespaces")
  public List<String> namespaces() {
    return client.namespaces().list().getItems().stream()
        .map(n -> n.getMetadata().getName())
        .sorted()
        .toList();
  }

  @GET
  @Path("/resources/{plural}")
  public Map<String, Object> listResources(
      @PathParam("plural") String plural, @QueryParam("namespace") String namespace) {
    String ns = namespace == null || namespace.isBlank() ? "default" : namespace;
    try {
      var ctx = ctx(plural);
      var list = client.genericKubernetesResources(ctx).inNamespace(ns).list();
      List<ResourceRow> rows = new ArrayList<>();
      for (GenericKubernetesResource r : list.getItems()) {
        rows.add(
            new ResourceRow(
                r.getMetadata().getName(),
                r.getMetadata().getNamespace(),
                phaseOf(r),
                Optional.ofNullable(r.getMetadata().getCreationTimestamp())
                    .map(Object::toString)
                    .orElse("—"),
                Optional.ofNullable(r.getMetadata().getUid()).orElse("")));
      }
      return Map.of("items", rows, "namespace", ns, "plural", plural);
    } catch (KubernetesClientException e) {
      return Map.of(
          "items",
          List.of(),
          "namespace",
          ns,
          "plural",
          plural,
          "warning",
          e.getMessage() != null ? e.getMessage() : e.toString());
    }
  }

  @GET
  @Path("/resources/{plural}/{name}")
  @Produces("text/yaml")
  public Response getYaml(
      @PathParam("plural") String plural,
      @PathParam("name") String name,
      @QueryParam("namespace") String namespace) {
    String ns = namespace == null || namespace.isBlank() ? "default" : namespace;
    try {
      var ctx = ctx(plural);
      GenericKubernetesResource r = client.genericKubernetesResources(ctx).inNamespace(ns).withName(name).get();
      if (r == null) {
        return Response.status(404).entity("Not found").type(MediaType.TEXT_PLAIN).build();
      }
      String yaml = Serialization.asYaml(r);
      return Response.ok(yaml).type("text/yaml; charset=UTF-8").build();
    } catch (Exception e) {
      return Response.status(500)
          .entity(e.getMessage() != null ? e.getMessage() : e.toString())
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  @DELETE
  @Path("/resources/{plural}/{name}")
  public Map<String, Object> deleteResource(
      @PathParam("plural") String plural,
      @PathParam("name") String name,
      @QueryParam("namespace") String namespace) {
    String ns = namespace == null || namespace.isBlank() ? "default" : namespace;
    var ctx = ctx(plural);
    var details = client.genericKubernetesResources(ctx).inNamespace(ns).withName(name).delete();
    boolean deleted = details != null && !details.isEmpty();
    return Map.of("deleted", deleted, "name", name, "namespace", ns);
  }

  @POST
  @Path("/apply")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response apply(Map<String, String> body) {
    String yaml = body != null ? body.get("yaml") : null;
    String ns = body != null ? body.get("namespace") : null;
    if (yaml == null || yaml.isBlank()) {
      return Response.status(400).entity(Map.of("error", "yaml is required")).build();
    }
    try {
      int n = 0;
      for (String doc : yaml.split("(?m)^---")) {
        if (doc.trim().isEmpty()) {
          continue;
        }
        GenericKubernetesResource item = Serialization.unmarshal(doc, GenericKubernetesResource.class);
        if (item == null || item.getKind() == null) {
          continue;
        }
        
        String targetNs = ns;
        if (item.getMetadata() != null && item.getMetadata().getNamespace() != null) {
          targetNs = item.getMetadata().getNamespace();
        }
        if (targetNs == null || targetNs.isBlank()) {
          targetNs = "default";
        }
        if (item.getMetadata() != null) {
          item.getMetadata().setNamespace(targetNs);
        }
        
        // Custom Resource Definition Context 추출
        String apiGroup = item.getApiVersion();
        String kind = item.getKind();
        String group = apiGroup.contains("/") ? apiGroup.substring(0, apiGroup.indexOf('/')) : "";
        String version = apiGroup.contains("/") ? apiGroup.substring(apiGroup.indexOf('/') + 1) : apiGroup;
        String plural = kind.toLowerCase() + "s";
        if (plural.endsWith("ys")) plural = plural.substring(0, plural.length() - 2) + "ies"; // simple pluralize
        if (plural.endsWith("ss")) plural = plural + "es";
        
        CustomResourceDefinitionContext ctx = new CustomResourceDefinitionContext.Builder()
            .withGroup(group)
            .withVersion(version)
            .withKind(kind)
            .withPlural(plural)
            .withScope("Namespaced")
            .build();
            
        client.genericKubernetesResources(ctx).inNamespace(targetNs).resource(item).createOrReplace();
        n++;
      }
      
      if (n == 0) {
        return Response.status(400).entity(Map.of("error", "유효한 리소스를 찾을 수 없습니다 (YAML 구문 확인)")).build();
      }
      return Response.ok(Map.of("applied", n)).build();
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.toString();
      return Response.status(400).entity(Map.of("error", msg)).build();
    }
  }

  @POST
  @Path("/test/ping")
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, Object> testPing(Map<String, Object> payload) {
    return Map.of(
        "ok",
        true,
        "receivedKeys",
        payload != null ? payload.keySet().size() : 0,
        "message",
        "대시보드 API 정상. health·kinds·apply로 클러스터와 연동하세요.");
  }

  private static CustomResourceDefinitionContext ctx(String plural) {
    return new CustomResourceDefinitionContext.Builder()
        .withGroup(GROUP)
        .withVersion(VERSION)
        .withPlural(plural)
        .withScope("Namespaced")
        .build();
  }

  private String phaseOf(GenericKubernetesResource r) {
    try {
      JsonNode node = objectMapper.convertValue(r, JsonNode.class);
      JsonNode p = node.path("status").path("phase");
      return p.isMissingNode() || p.isNull() ? "—" : p.asText("—");
    } catch (Exception e) {
      return "—";
    }
  }
}
