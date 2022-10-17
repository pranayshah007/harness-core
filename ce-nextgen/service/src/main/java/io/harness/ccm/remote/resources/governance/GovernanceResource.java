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
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.GovernanceEnqueueResponseDTO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json"})
@Tag(name = "Policy", description = "This contains APIs related to Policy Management ")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
//@PublicApi
public class GovernanceResource {
  private final PolicyService policyService;
  private final CCMRbacHelper rbacHelper;
  @Inject
  public GovernanceResource(PolicyService policyService, CCMRbacHelper rbacHelper) {
    this.policyService = policyService;
    this.rbacHelper = rbacHelper;
  }

  // Internal API for OOTB policy creation

  @POST
  //  @Hidden
  //  @InternalApi
  @Path("policy")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new policy internal api", nickname = "addPolicyNameInternal")
  @Operation(operationId = "addPolicyNameInternal", summary = "Add a new OOTB policy to be executed",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created policy")
      })
  public ResponseDTO<Policy>
  create(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Policy store object") @Valid CreatePolicyDTO createPolicyDTO) {
    // rbacHelper.checkPolicyEditPermission(accountId, null, null);
    Policy policy = createPolicyDTO.getPolicy();
    policy.setAccountId(accountId);
    policyService.save(policy);
    return ResponseDTO.newResponse(policy.toDTO());
  }

  // Update a policy already made

  @PUT
  //  @Hidden
  //  @InternalApi
  @Path("policy")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a existing OOTB Policy", nickname = "updatePolicy")
  @LogAccountIdentifier
  @Operation(operationId = "updatePolicy", description = "Update a Policy", summary = "Update a Policy",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update a existing OOTB Policy",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Policy>
  updatePolicy(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing ceViewFolder object") @Valid CreatePolicyDTO createPolicyDTO) {
    // rbacHelper.checkPolicyEditPermission(accountId, null, null);
    Policy policy = createPolicyDTO.getPolicy();
    policy.toDTO();
    policy.setAccountId(accountId);
    policyService.update(policy);
    return ResponseDTO.newResponse(policy);
  }

  // Internal API for deletion of OOTB policies

  @DELETE
  //  @Hidden
  //  @InternalApi
  @Path("policy/{policyId}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a policy", nickname = "deletePolicy")
  @LogAccountIdentifier
  @Operation(operationId = "deletePolicy", description = "Delete a Policy for the given a ID.",
      summary = "Delete a policy",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("policyId") @Parameter(
          required = true, description = "Unique identifier for the policy") @NotNull @Valid String uuid) {
    // rbacHelper.checkPolicyDeletePermission(accountId, null, null);
    boolean result = policyService.delete(accountId, uuid);
    return ResponseDTO.newResponse(result);
  }

  // API to list all OOTB Policies

  @POST
  @Path("policy/list")
  @ApiOperation(value = "Get OOTB policies for account", nickname = "getPolicies")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPolicies", description = "Fetch policies ", summary = "Fetch policies for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns List of policies", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<Policy>>
  listPolicy(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing ceViewFolder object") @Valid ListDTO listDTO) {
    // rbacHelper.checkPolicyViewPermission(accountId, null, null);
    PolicyRequest query = listDTO.getPolicyRequest();
    List<Policy> Policies = new ArrayList<>();
    query.setAccountId(accountId);
    String uuid = query.getUuid();
    String isStablePolicy = query.getIsStablePolicy();
    String resource = query.getResource();
    String tags = query.getTags();
    if (uuid != null) {
      Policy policy = policyService.listid(accountId, uuid);
      Policies.add(policy);
      return ResponseDTO.newResponse(Policies);
    }
    if (isStablePolicy != null) {
      Policies = policyService.findByStability(isStablePolicy, accountId);
      return ResponseDTO.newResponse(Policies);
    }
    if (resource != null && tags == null) {
      Policies = policyService.findByResource(resource, accountId);
      return ResponseDTO.newResponse(Policies);
    }
    if (resource == null && tags != null) {
      Policies = policyService.findByTag(tags, accountId);
      return ResponseDTO.newResponse(Policies);
    }
    if (resource != null && tags != null) {
      Policies = policyService.findByTagAndResource(resource, tags, accountId);
      return ResponseDTO.newResponse(Policies);
    }

    Policies = policyService.list(accountId);
    return ResponseDTO.newResponse(Policies);
  }

  @POST
  @Path("enqueue")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Enqueues job for execution", nickname = "enqueueGovernanceJob")
  // TODO: Also check with PL team as this does not require accountId to be passed, how to add accountId in the log
  // context here ?
  @Operation(operationId = "enqueueGovernanceJob", description = "Enqueues job for execution.",
      summary = "Enqueues job for execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns success when job is enqueued",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernanceEnqueueResponseDTO>
  enqueue(@RequestBody(required = true, description = "Request body for queuing the governance job")
      @Valid GovernanceJobEnqueueDTO governanceJobEnqueueDTO) {
    log.info("Policy setid is {}", governanceJobEnqueueDTO.getPolicySetId());
    // Next is fetch from Mongo this policySetId and enqueue in the Faktory queue one by one.
    return ResponseDTO.newResponse();
  }
}
