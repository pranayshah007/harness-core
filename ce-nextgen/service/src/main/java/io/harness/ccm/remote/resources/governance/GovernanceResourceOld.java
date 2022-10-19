/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dto.GovernancePolicySet;
import io.harness.ccm.views.dto.GovernancePolicySetResponseDTO;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.stereotype.Service;

@Api("governance")
@Path("governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Perspectives Folders",
    description = "Group your Perspectives using Folders in ways that are more meaningful to your business needs.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class GovernanceResourceOld {
  private final GovernancePolicyService governancePolicyService;

  @Inject
  public GovernanceResourceOld(GovernancePolicyService governancePolicyService) {
    this.governancePolicyService = governancePolicyService;
  }

  @POST
  @Path("policyset")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates governance policy set", nickname = "governancePolicySet")
  @Operation(operationId = "governancePolicySet", description = "Creates governance policy set",
      summary = "Creates governance policy set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns success when policySet is created",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernancePolicySetResponseDTO>
  policyset(@RequestBody(required = true,
                description = "Request body for creating a policy set") @Valid GovernancePolicySet governancePolicySet,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    rbacHelper.checkFolderEditPermission(accountId, null, null);
    log.info("Policy set dto is");

    //
    //        if (isEmpty(governancePolicySet.getCloudProvider())) {
    //            throw new InvalidRequestException("cloudProvider type cannot be null or empty");
    //        }
    governancePolicyService.save(governancePolicySet) return ResponseDTO.newResponse();
  }
}
