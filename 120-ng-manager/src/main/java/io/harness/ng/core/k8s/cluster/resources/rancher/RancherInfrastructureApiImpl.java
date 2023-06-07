/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.ApiUtils.addLinksHeader;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.infrastructure.resource.InfrastructureHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.RancherInfrastructureApi;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDP)
public class RancherInfrastructureApiImpl implements RancherInfrastructureApi {
  @Inject RancherClusterService rancherService;
  @Inject InfrastructureHelper infraHelper;

  @Override
  public Response listAccountScopedRancherClustersUsingConnector(
      String connector, String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort, String order) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, null, null);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, null, null, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listAccountScopedRancherClustersUsingEnvAndInfra(String environment, String infrastructureDefinition,
      String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort, String order) {
    IdentifierRef connectorRef =
        infraHelper.getConnectorRef(harnessAccount, null, null, environment, infrastructureDefinition);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, null, null, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listOrgScopedRancherClustersUsingConnector(String org, String connector, String harnessAccount,
      Integer page, @Max(1000L) Integer limit, String sort, String order) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, org, null);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, null, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listOrgScopedRancherClustersUsingEnvAndInfra(String org, String environment,
      String infrastructureDefinition, String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort,
      String order) {
    IdentifierRef connectorRef =
        infraHelper.getConnectorRef(harnessAccount, org, null, environment, infrastructureDefinition);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, null, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listProjectScopedRancherClustersUsingConnector(String org, String project, String connector,
      String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort, String order) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connector, harnessAccount, org, project);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, project, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  @Override
  public Response listProjectScopedRancherClustersUsingEnvAndInfra(String org, String project, String environment,
      String infrastructureDefinition, String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort,
      String order) {
    IdentifierRef connectorRef =
        infraHelper.getConnectorRef(harnessAccount, org, project, environment, infrastructureDefinition);
    Map<String, String> pageRequestParams = createPageRequestParamsMap(page, limit, sort, order);
    RancherClusterListResponseDTO responseDTO =
        rancherService.listClusters(harnessAccount, org, project, connectorRef, pageRequestParams);
    return generateResponseWithHeaders(responseDTO, page, limit);
  }

  private Response generateResponseWithHeaders(RancherClusterListResponseDTO responseDTO, Integer page, Integer limit) {
    ResponseBuilder responseBuilder = Response.ok().entity(responseDTO);
    addLinksHeader(
        responseBuilder, isNotEmpty(responseDTO.getClusters()) ? responseDTO.getClusters().size() : 0, page, limit);
    return responseBuilder.build();
  }

  private Map<String, String> createPageRequestParamsMap(Integer page, Integer limit, String sort, String order) {
    Map<String, String> pageRequestParamsMap = new HashMap<>();
    if (page != null) {
      pageRequestParamsMap.put("page", String.valueOf(page));
    }
    if (limit != null) {
      pageRequestParamsMap.put("limit", String.valueOf(limit));
    }
    if (isNotEmpty(order)) {
      pageRequestParamsMap.put("order", order);
    }
    if (isNotEmpty(sort)) {
      pageRequestParamsMap.put("sort", sort);
    }
    return pageRequestParamsMap;
  }
}
