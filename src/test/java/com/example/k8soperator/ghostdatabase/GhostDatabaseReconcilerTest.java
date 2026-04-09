package com.example.k8soperator.ghostdatabase;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@EnableKubernetesMockClient(crud = true)
class GhostDatabaseReconcilerTest {

  KubernetesMockServer server;
  KubernetesClient client;
  GhostDatabaseReconciler reconciler;

  @BeforeEach
  void setUp() {
    reconciler = new GhostDatabaseReconciler(client);
  }

  @Test
  void reconcileCreatesDeploymentServiceAndPvc() {
    client.namespaces().resource(new io.fabric8.kubernetes.api.model.NamespaceBuilder().withNewMetadata().withName("ns1").endMetadata().build()).create();

    GhostDatabase cr = new GhostDatabase();
    cr.setMetadata(
        new ObjectMetaBuilder().withName("t1").withNamespace("ns1").withResourceVersion("1").build());
    cr.setSpec(new GhostDatabaseSpec());
    cr.getSpec().setDatabaseName("db1");
    cr.getSpec().setStorageSize("1Gi");

    @SuppressWarnings("unchecked")
    Context<GhostDatabase> ctx = Mockito.mock(Context.class);
    var uc = reconciler.reconcile(cr, ctx);
    assertThat(uc.getResource()).isPresent();
    assertThat(uc.getResource().orElseThrow().getStatus().getPhase()).isEqualTo("Ready");

    assertThat(client.persistentVolumeClaims().inNamespace("ns1").list().getItems()).hasSize(1);
    assertThat(client.secrets().inNamespace("ns1").list().getItems()).hasSize(1);
    assertThat(client.apps().deployments().inNamespace("ns1").list().getItems()).hasSize(1);
    assertThat(client.services().inNamespace("ns1").list().getItems()).hasSize(1);
  }

  @Test
  void reconcileFailsWhenDatabaseNameMissing() {
    client.namespaces().resource(new io.fabric8.kubernetes.api.model.NamespaceBuilder().withNewMetadata().withName("ns2").endMetadata().build()).create();

    GhostDatabase cr = new GhostDatabase();
    cr.setMetadata(new ObjectMetaBuilder().withName("bad").withNamespace("ns2").withResourceVersion("1").build());
    cr.setSpec(new GhostDatabaseSpec());
    cr.getSpec().setStorageSize("1Gi");

    @SuppressWarnings("unchecked")
    Context<GhostDatabase> ctx = Mockito.mock(Context.class);
    var uc = reconciler.reconcile(cr, ctx);
    assertThat(uc.getResource().orElseThrow().getStatus().getPhase()).isEqualTo("Failed");
  }
}
