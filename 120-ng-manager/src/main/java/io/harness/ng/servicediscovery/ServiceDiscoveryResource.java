/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.k8sinlinemanifest.K8sInlineManifestService;
import io.harness.ng.k8sinlinemanifest.K8sManifestRequest;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import retrofit.http.Body;

@OwnedBy(HarnessTeam.CHAOS)
@Api(value = "servicediscovery", hidden = true)
@Path("servicediscovery")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Slf4j
@NextGenManagerAuth
public class ServiceDiscoveryResource {
  K8sInlineManifestService k8sInlineManifestService;

  @POST
  @InternalApi
  @ApiOperation(
      value = "Apply K8s manifest for service discovery", nickname = "serviceDiscoveryK8sApply", hidden = true)
  public ResponseDTO<String>
  applyServiceDiscoveryK8sManifest(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank
                                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body K8sManifestRequest request) {
    String uid = generateUuid();
    String taskId = k8sInlineManifestService.applyK8sManifest(request, uid, new ServiceDiscoveryNotifyCallback(uid));
    return ResponseDTO.newResponse(taskId);
  }
}
