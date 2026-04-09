package com.example.k8soperator.resourcequota;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("operator.example.com")
@Version("v1alpha1")
@Kind("ResourceQuotaGuard")
@Plural("resourcequotaguards")
public class ResourceQuotaGuard extends CustomResource<ResourceQuotaGuardSpec, ResourceQuotaGuardStatus>
    implements Namespaced {}
