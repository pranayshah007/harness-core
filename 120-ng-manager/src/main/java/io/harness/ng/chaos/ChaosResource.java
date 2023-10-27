/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

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
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import retrofit.http.Body;

@OwnedBy(HarnessTeam.CE)
@Api(value = "chaos", hidden = true)
@Path("chaos")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Slf4j
@NextGenManagerAuth
public class ChaosResource {
  ChaosService chaosService;
  K8sInlineManifestService k8sInlineManifestService;

  @POST
  @InternalApi
  @ApiOperation(value = "Apply K8s manifest for chaos", nickname = "chaosK8sApply", hidden = true)
  public ResponseDTO<String> applyChaosK8sManifest(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body K8sManifestRequest request) {
    String uid = generateUuid();
    String taskId = k8sInlineManifestService.applyK8sManifest(request, uid, new ChaosNotifyCallback(uid));
    return ResponseDTO.newResponse(taskId);
  }

  @POST
  @Path("/notify")
  @InternalApi
  @ApiOperation(value = "Notify on completion of chaos experiment", nickname = "chaosStepNotify", hidden = true)
  public ResponseDTO<Boolean> chaosStepNotify(@Body ChaosStepNotifyResponse stepNotifyResponse) {
    try {
      chaosService.notifyStep(stepNotifyResponse.getNotifyId(), stepNotifyResponse.getData());
      return ResponseDTO.newResponse(true);
    } catch (Exception e) {
      return ResponseDTO.newResponse(true);
    }
  }

  @POST
  @Path("/chaosInfrastructure")
  @InternalApi
  @ApiOperation(value = "Register the chaos infrastructure entity with the parent environment",
      nickname = "registerChaosInfrastructure", hidden = true)
  public ResponseDTO<Boolean>
  registerChaosInfrastructure(@Body ChaosInfrastructureRequest chaosInfrastructureRequest) {
    boolean result = chaosService.registerChaosInfrastructure(chaosInfrastructureRequest);
    return ResponseDTO.newResponse(result);
  }

  @DELETE
  @Path("/chaosInfrastructure")
  @InternalApi
  @ApiOperation(value = "Deregister the chaos infrastructure entity relation with the parent environment",
      nickname = "deleteChaosInfrastructure", hidden = true)
  public ResponseDTO<Boolean>
  deleteChaosInfrastructure(@Body ChaosInfrastructureRequest chaosInfrastructureRequest) {
    boolean result = chaosService.deleteChaosInfrastructure(chaosInfrastructureRequest);
    return ResponseDTO.newResponse(result);
  }
}