/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.infrastructure.resource.InfrastructureHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.OrgRancherInfrastructureApi;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDP)
public class OrgRancherInfrastructureApiImpl implements OrgRancherInfrastructureApi {
  @Inject RancherClusterService rancherService;
  @Inject RancherClusterHelper rancherHelper;
  @Inject InfrastructureHelper infraHelper;

  @Override
  public Response listOrgScopedRancherClustersUsingConnector(String org, String connector, String harnessAccount,
      Integer page, @Max(1000L) Integer limit, String sort, String order) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, org, null);
    Map<String, String> pageRequestParams = rancherHelper.createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, null, connectorRef, pageRequestParams);
    return rancherHelper.generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listOrgScopedRancherClustersUsingEnvAndInfra(String org, String environment,
      String infrastructureDefinition, String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort,
      String order) {
    IdentifierRef connectorRef =
        infraHelper.getConnectorRef(harnessAccount, org, null, environment, infrastructureDefinition);
    Map<String, String> pageRequestParams = rancherHelper.createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, null, connectorRef, pageRequestParams);
    return rancherHelper.generateResponseWithHeaders(responseDTO, page, limit);
  }
}
