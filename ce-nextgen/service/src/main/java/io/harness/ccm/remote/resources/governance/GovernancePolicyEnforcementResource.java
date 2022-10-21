/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.gson.Gson;
import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.scheduler.SchedulerClient;
import io.harness.ccm.scheduler.SchedulerDTO;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreatePolicyEnforcementDTO;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyEnforcementService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.PublicApi;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.stereotype.Service;
import retrofit2.Response;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CE;

@Slf4j
@Service
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json"})
@Tag(name = "PolicyEnforcement", description = "This contains APIs related to Policy Enforcement ")
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
//@NextGenManagerAuth
@PublicApi
public class GovernancePolicyEnforcementResource {
  public static final String ACCOUNT_ID = "accountId";
  public static final String EXECUTION_SCHEDULE = "executionSchedule";
  public static final String POLICY_ENFORCEMENT_ID = "policyEnforcementId";
  public static final String EXECUTOR = "http";
  public static final String METHOD = "POST";
  public static final String ENFORCEMENT_ID = "enforcementId";
  private final PolicyEnforcementService policyEnforcementService;
  private final CCMRbacHelper rbacHelper;
  private final GovernancePolicyService policyService;
  private final PolicyPackService policyPackService;
  @Inject CENextGenConfiguration configuration;
  @Inject SchedulerClient schedulerClient;

  @Inject
  public GovernancePolicyEnforcementResource(PolicyEnforcementService policyEnforcementService,
      CCMRbacHelper rbacHelper, GovernancePolicyService governancePolicyService, PolicyPackService policyPackService) {
    this.policyEnforcementService = policyEnforcementService;
    this.rbacHelper = rbacHelper;
    this.policyService = governancePolicyService;
    this.policyPackService = policyPackService;
  }

  @POST
  @Hidden
  @Path("enforcement")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new policy Enforcement api", nickname = "addPolicyEnforcement")
  @Operation(operationId = "addPolicyEnforcement", summary = "Add a new policy Enforcement ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created policy")
      })
  public ResponseDTO<PolicyEnforcement>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing Policy store object")
      @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
    // rbacHelper.checkPolicyEnforcementEditPermission(accountId, null, null);
    PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
    policyEnforcement.setAccountId(accountId);
    if (policyEnforcementService.listid(accountId, policyEnforcement.getUuid(), true) != null) {
      throw new InvalidRequestException("Policy Enforcement with this uuid already exits");
    }
    //TODO: Re enable after testing
    //policyService.check(accountId, policyEnforcement.getPolicyIds());
    //policyPackService.check(accountId, policyEnforcement.getPolicyPackIDs());
    policyEnforcementService.save(policyEnforcement);

    // Insert a record in dkron
    // TODO: Add support for GCP cloud scheduler as well.
    if (configuration.getGovernanceConfig().isUseDkron()) {
      log.info("Use dkron is enabled in config");
      try {
        // This will be read by the enqueue api during callback
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(POLICY_ENFORCEMENT_ID, policyEnforcement.getUuid());

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ACCOUNT_ID, policyEnforcement.getAccountId());
        metadata.put(EXECUTION_SCHEDULE, policyEnforcement.getExecutionSchedule());
        metadata.put(ENFORCEMENT_ID, policyEnforcement.getUuid());

        SchedulerDTO schedulerDTO =
            SchedulerDTO.builder()
                .schedule(policyEnforcement.getExecutionSchedule())
                .disabled(configuration.getGovernanceConfig().isDkronJobEnabled())
                .name(policyEnforcement.getUuid().toLowerCase())
                .displayname(policyEnforcement.getName() + "_" + policyEnforcement.getUuid())
                .timezone(policyEnforcement.getExecutionTimezone())
                .executor(EXECUTOR)
                .metadata(metadata)
                .executor_config(SchedulerDTO.ExecutorConfig.builder()
                                     .method(METHOD)
                                     .url(configuration.getGovernanceConfig().getCallbackApiEndpoint())
                                     .body(jsonObject.toString())
                                     .headers(Arrays.asList("Content-Type: application/json"))
                                     .build())
                .build();
        log.info(new Gson().toJson(schedulerDTO));
        okhttp3.RequestBody body =
            okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), new Gson().toJson(schedulerDTO));
        Response res = schedulerClient.createSchedule(body).execute();
        log.info("code: {}, message: {}, body: {}", res.code(), res.message(), res.body());
      } catch (Exception e) {
        log.info("{}", e.toString());
      }
    }
    return ResponseDTO.newResponse(policyEnforcement.toDTO());
  }

  @DELETE
  @Hidden
  @Path("enforcement/{enforcementID}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a policy", nickname = "deletePolicy")
  @LogAccountIdentifier
  @Operation(operationId = "deletePolicyEnforcement", description = "Delete a Policy enforcement for the given a ID.",
      summary = "Delete a policy enforcement",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("enforcementID") @Parameter(
          required = true, description = "Unique identifier for the policy enforcement") @NotNull @Valid String uuid) {
    // rbacHelper.checkPolicyEnforcementDeletePermission(accountId, null, null);
    policyEnforcementService.listid(accountId, uuid, false);
    boolean result = policyEnforcementService.delete(accountId, uuid);
    // TODO: Delete the record from dkron as well.
    return ResponseDTO.newResponse(result);
  }

  @PUT
  @Hidden
  @Path("enforcement")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a Policy enforcement", nickname = "updateEnforcement")
  @LogAccountIdentifier
  @Operation(operationId = "updateEnforcement", description = "Update a Policy enforcement",
      summary = "Update a Policy enforcement",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update a existing Policy enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<PolicyEnforcement>
  update(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing policy enforcement object")
      @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
    //  rbacHelper.checkPolicyEnforcementEditPermission(accountId, null, null);
    PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
    policyEnforcement.setAccountId(accountId);
    policyEnforcementService.listid(accountId, policyEnforcement.getUuid(), false);
    policyService.check(accountId, policyEnforcement.getPolicyIds());
    policyPackService.check(accountId, policyEnforcement.getPolicyPackIDs());
    policyEnforcementService.update(policyEnforcement);
    // TODO: Update the record in dkron as well.
    return ResponseDTO.newResponse(policyEnforcement.toDTO());
  }

  @POST
  @Path("enforcement/list")
  @ApiOperation(value = "Get enforcement list", nickname = "getPolicyEnforcement")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPolicyEnforcement", description = "Fetch Policy Enforcement ",
      summary = "Fetch Policy Enforcement for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of policies  Enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<PolicyEnforcement>>
  listPolicyEnforcements(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing  Policy Enforcement  object")
      @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
    // rbacHelper.checkPolicyEnforcementViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(policyEnforcementService.list(accountId));
  }
}