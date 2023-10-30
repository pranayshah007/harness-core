/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datapointsdata.service.KubernetesDataPointsService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.KubernetesDataPointsApi;
import io.harness.spec.server.idp.v1.model.KubernetesRequest;

import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
public class KubernetesDataPointsApiImpl implements KubernetesDataPointsApi {
  private KubernetesDataPointsService kubernetesDataPointsService;
  @Override
  public Response getK8sDataPointValues(@Valid KubernetesRequest body, String harnessAccount) {
    try {
      log.info("Kubernetes API called - request body - {}, account - {}", body, harnessAccount);
      Map<String, Object> returnData =
          kubernetesDataPointsService.getDataPointDataValues(harnessAccount, body.getRequest());
      return Response.status(Response.Status.OK).entity(returnData).build();
    } catch (Exception e) {
      log.error("Error in getting data from kubernetes datasource - {} for account - {}", "harness", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
