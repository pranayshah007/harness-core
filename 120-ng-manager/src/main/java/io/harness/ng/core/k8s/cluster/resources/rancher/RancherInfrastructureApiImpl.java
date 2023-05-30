/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.infrastructure.resource.InfrastructureHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.RancherInfrastructureApi;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDP)
public class RancherInfrastructureApiImpl implements RancherInfrastructureApi {
  @Inject RancherClusterService rancherService;

  @Override
  public Response listAccountScopedRancherClustersUsingConnector(String connector, String harnessAccount) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, null, null);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, null, null, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }

  @Override
  public Response listAccountScopedRancherClustersUsingEnvAndInfra(
      String environment, String infrastructureDefinition, String harnessAccount) {
    IdentifierRef connectorRef =
        InfrastructureHelper.getConnectorRef(harnessAccount, null, null, environment, infrastructureDefinition);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, null, null, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }

  @Override
  public Response listOrgScopedRancherClustersUsingConnector(String org, String connector, String harnessAccount) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, org, null);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, org, null, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }

  @Override
  public Response listOrgScopedRancherClustersUsingEnvAndInfra(
      String org, String environment, String infrastructureDefinition, String harnessAccount) {
    IdentifierRef connectorRef =
        InfrastructureHelper.getConnectorRef(harnessAccount, org, null, environment, infrastructureDefinition);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, org, null, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }

  @Override
  public Response listProjectScopedRancherClustersUsingConnector(
      String org, String project, String connector, String harnessAccount) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, org, project);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, org, project, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }

  @Override
  public Response listProjectScopedRancherClustersUsingEnvAndInfra(
      String org, String project, String environment, String infrastructureDefinition, String harnessAccount) {
    IdentifierRef connectorRef =
        InfrastructureHelper.getConnectorRef(harnessAccount, org, project, environment, infrastructureDefinition);
    RancherClusterListResponseDTO responseDTO = rancherService.listClusters(harnessAccount, org, project, connectorRef);
    return Response.ok().entity(responseDTO).build();
  }
}
