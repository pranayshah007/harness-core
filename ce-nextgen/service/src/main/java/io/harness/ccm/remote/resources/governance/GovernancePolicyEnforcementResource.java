/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import com.google.inject.name.Named;
import static io.harness.annotations.dev.HarnessTeam.CE;
import io.harness.ccm.audittrails.events.PolicyEnforcementCreateEvent;
import io.harness.ccm.audittrails.events.PolicyEnforcementDeleteEvent;
import io.harness.ccm.audittrails.events.PolicyEnforcementUpdateEvent;
import io.harness.ccm.audittrails.events.PolicyPackCreateEvent;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_DELETE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_UPDATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.remote.resources.TelemetryConstants.POLICY_ENFORCEMENT_NAME;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import io.harness.outbox.api.OutboxService;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.PolicyEnforcementCreateEvent;
import io.harness.ccm.audittrails.events.PolicyEnforcementDeleteEvent;
import io.harness.ccm.audittrails.events.PolicyEnforcementUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.scheduler.SchedulerClient;
import io.harness.ccm.scheduler.SchedulerDTO;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreatePolicyEnforcementDTO;
import io.harness.ccm.views.dto.EnforcementCountDTO;
import io.harness.ccm.views.entities.EnforcementCount;
import io.harness.ccm.views.entities.EnforcementCountRequest;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyEnforcementService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.PublicApi;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;
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
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.minidev.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.minidev.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_DELETE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_POLICY_ENFORCEMENT_UPDATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.remote.resources.TelemetryConstants.POLICY_ENFORCEMENT_NAME;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

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
  private final TelemetryReporter telemetryReporter;
  @Inject CENextGenConfiguration configuration;
  @Inject SchedulerClient schedulerClient;
  @Inject private OutboxService outboxService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public GovernancePolicyEnforcementResource(PolicyEnforcementService policyEnforcementService,
      CCMRbacHelper rbacHelper, GovernancePolicyService governancePolicyService, PolicyPackService policyPackService,
      TelemetryReporter telemetryReporter) {
    this.policyEnforcementService = policyEnforcementService;
    this.rbacHelper = rbacHelper;
    this.policyService = governancePolicyService;
    this.policyPackService = policyPackService;
    this.telemetryReporter = telemetryReporter;
  }

  @POST
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
      @RequestBody(required = true, description = "Request body containing Policy Enforcement object")
      @Valid CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
    // rbacHelper.checkPolicyEnforcementEditPermission(accountId, null, null);
    if (createPolicyEnforcementDTO == null) {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
    policyEnforcement.setAccountId(accountId);
    if (policyEnforcement.getExecutionTimezone() == null) {
      policyEnforcement.setExecutionTimezone("UTC");
    }

    try {
      CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(
          policyEnforcement.getExecutionSchedule(), TimeZone.getTimeZone(policyEnforcement.getExecutionTimezone()));
      CronSequenceGenerator.isValidExpression(String.valueOf(cronSequenceGenerator));
      // TODO: Timezone and Cron validation needs to be moved to a non deprecated method
    } catch (Exception e) {
      throw new InvalidRequestException("cron is not valid");
    }
    if (policyEnforcementService.listName(accountId, policyEnforcement.getName(), true) != null) {
      throw new InvalidRequestException("Policy Enforcement with given name already exits");
    }

    policyService.check(accountId, policyEnforcement.getPolicyIds());
    policyPackService.check(policyEnforcement.getPolicyPackIDs());
    policyEnforcementService.save(policyEnforcement);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_ENFORCEMENT_NAME, policyEnforcement.getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_ENFORCEMENT_CREATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

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
        JSONArray headers = new JSONArray();
        headers.add("Content-Type: application/json");
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
                                     .headers(headers.toString())
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
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new PolicyEnforcementCreateEvent(accountId, policyEnforcement));
      return policyEnforcementService.listName(accountId, policyEnforcement.getName(), false);
    })));
  }

  @DELETE
  @Hidden
  @Path("enforcement/{enforcementID}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a policy", nickname = "deletePolicyEnforcement")
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

       // TODO: Delete the record from dkron as well.

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_ENFORCEMENT_NAME, policyEnforcementService.listId(accountId, uuid, false).getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_ENFORCEMENT_DELETE, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new PolicyEnforcementDeleteEvent(accountId, policyEnforcementService.listId(accountId, uuid, false)));
      return   policyEnforcementService.delete(accountId, uuid);
    })));
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
    if (createPolicyEnforcementDTO == null) {
      throw new InvalidRequestException("Request payload is malformed");
    }
    PolicyEnforcement policyEnforcement = createPolicyEnforcementDTO.getPolicyEnforcement();
    policyEnforcement.setAccountId(accountId);
    policyEnforcementService.listName(accountId, policyEnforcement.getName(), false);
    if (policyEnforcement.getPolicyIds() != null) {
      policyService.check(accountId, policyEnforcement.getPolicyIds());
    }
    if (policyEnforcement.getPolicyPackIDs() != null) {
      policyPackService.check(policyEnforcement.getPolicyPackIDs());
    }
    // TODO: Update the record in dkron as well.
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(POLICY_ENFORCEMENT_NAME, policyEnforcement.getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_POLICY_ENFORCEMENT_UPDATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new PolicyEnforcementUpdateEvent(accountId, policyEnforcement));
      return policyEnforcementService.update(policyEnforcement);
    })));
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
      @RequestBody(required = true, description = "Request body containing  Policy Enforcement  object") @Valid
      @NotNull CreatePolicyEnforcementDTO createPolicyEnforcementDTO) {
    // rbacHelper.checkPolicyEnforcementViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(policyEnforcementService.list(accountId));
  }

  @POST
  @Path("enforcement/count")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get enforcement count", nickname = "getPolicyEnforcementCount")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPolicyEnforcementCount", description = "Fetch Policy Enforcement count",
      summary = "Fetch Policy Enforcement count for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of policies  Enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<EnforcementCount>
  enforcementCount(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing  Policy Enforcement count object")
      @Valid EnforcementCountDTO enforcementCountDTO) {
    if (enforcementCountDTO == null) {
      throw new InvalidRequestException("Request payload is malformed");
    }
    EnforcementCountRequest enforcementCountRequest = enforcementCountDTO.getEnforcementCountRequest();
    log.info("{}", enforcementCountRequest);
    return ResponseDTO.newResponse(policyEnforcementService.getCount(accountId, enforcementCountRequest));
  }
}