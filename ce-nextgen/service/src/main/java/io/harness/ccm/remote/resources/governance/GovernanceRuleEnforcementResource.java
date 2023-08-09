/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.CLOUD_PROVIDER;
import static io.harness.ccm.TelemetryConstants.EXECUTION_SCHEDULE;
import static io.harness.ccm.TelemetryConstants.EXECUTION_TIMEZONE;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_ENFORCEMENT_CREATED;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_ENFORCEMENT_DELETE;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_ENFORCEMENT_UPDATED;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.TelemetryConstants.RULE_ENFORCEMENT_NAME;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_EXECUTE;
import static io.harness.ccm.rbac.CCMResources.GOVERNANCE_CONNECTOR;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.RuleEnforcementCreateEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementDeleteEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.scheduler.SchedulerClient;
import io.harness.ccm.scheduler.SchedulerDTO;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreateRuleEnforcementDTO;
import io.harness.ccm.views.dto.EnforcementCountDTO;
import io.harness.ccm.views.dto.ExecutionDetailDTO;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.EnforcementCount;
import io.harness.ccm.views.helper.EnforcementCountRequest;
import io.harness.ccm.views.helper.ExecutionDetailRequest;
import io.harness.ccm.views.helper.ExecutionDetails;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.GovernanceConfig;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
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
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Response;

@Slf4j
@Singleton
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json"})
@Tag(name = "RuleEnforcement", description = "This contains APIs related to Rule Enforcement ")
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
public class GovernanceRuleEnforcementResource {
  public static final String ACCOUNT_ID = "accountId";
  public static final String EXECUTION_SCHEDULE_LOCAL = "executionSchedule";
  public static final String RULE_ENFORCEMENT_ID = "ruleEnforcementId";
  public static final String SCHEDULER_EXECUTOR = "http";
  public static final String SCHEDULER_HTTP_METHOD = "POST";
  public static final String ENFORCEMENT_ID = "enforcementId";
  public static final String CONTENT_TYPE_APPLICATION_JSON = "Content-Type: application/json";
  public static final String SCHEDULER_HTTP_TIMEOUT = "200";
  public static final String SCHEDULER_IS_DEBUG = "true";
  public static final String CODE_MESSAGE_BODY = "code: {}, message: {}, body: {}";
  private final RuleEnforcementService ruleEnforcementService;
  private final CCMRbacHelper rbacHelper;
  private final TelemetryReporter telemetryReporter;
  private final CENextGenConfiguration configuration;
  private final RuleSetService ruleSetService;
  private final GovernanceRuleService governanceRuleService;
  @Inject SchedulerClient schedulerClient;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryRule = DEFAULT_RETRY_POLICY;
  public static final String MALFORMED_ERROR = "Request payload is malformed";
  @Inject
  public GovernanceRuleEnforcementResource(RuleEnforcementService ruleEnforcementService,
      TelemetryReporter telemetryReporter, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      OutboxService outboxService, CENextGenConfiguration configuration, CCMRbacHelper rbacHelper,
      RuleSetService ruleSetService, GovernanceRuleService governanceRuleService) {
    this.ruleEnforcementService = ruleEnforcementService;
    this.rbacHelper = rbacHelper;
    this.telemetryReporter = telemetryReporter;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.configuration = configuration;
    this.ruleSetService = ruleSetService;
    this.governanceRuleService = governanceRuleService;
  }

  @POST
  @Path("enforcement")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new rule Enforcement api", nickname = "addRuleEnforcement")
  @Operation(operationId = "addRuleEnforcement", summary = "Add a new rule Enforcement ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created rule")
      })
  public ResponseDTO<RuleEnforcement>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing Rule Enforcement object")
      @Valid CreateRuleEnforcementDTO createRuleEnforcementDTO) {
    rbacHelper.checkRuleEnforcementEditPermission(accountId, null, null);
    if (createRuleEnforcementDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleEnforcement ruleEnforcement = createRuleEnforcementDTO.getRuleEnforcement();
    Set<String> rules = new HashSet<>();
    if (ruleEnforcement.getRuleIds() != null && ruleEnforcement.getRuleIds().size() > 0) {
      governanceRuleService.check(accountId, ruleEnforcement.getRuleIds());
      rules.addAll(ruleEnforcement.getRuleIds());
    }
    if (ruleEnforcement.getRuleSetIDs() != null && ruleEnforcement.getRuleSetIDs().size() > 0) {
      ruleSetService.check(accountId, ruleEnforcement.getRuleSetIDs());
      List<RuleSet> ruleSets = ruleSetService.listPacks(accountId, ruleEnforcement.getRuleSetIDs());
      for (RuleSet ruleSet : ruleSets) {
        rules.addAll(ruleSet.getRulesIdentifier());
      }
    }
    if (rules.size() < 1) {
      throw new InvalidRequestException("At least one rule should be added in ruleIds/ruleSetIDs");
    }
    if (ruleEnforcement.getCloudProvider() == null) {
      ruleEnforcement.setCloudProvider(
          governanceRuleService.fetchById(accountId, rules.iterator().next(), false).getCloudProvider());
    }
    ruleSetService.validateCloudProvider(accountId, rules, ruleEnforcement.getCloudProvider());
    Set<String> rulesPermitted = rbacHelper.checkRuleIdsGivenPermission(accountId, null, null, rules, RULE_EXECUTE);
    if (rulesPermitted.size() != rules.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE),
          WingsException.USER, null);
    }
    Set<ConnectorInfoDTO> nextGenConnectorResponses = governanceRuleService.getConnectorResponse(
        accountId, new HashSet<>(ruleEnforcement.getTargetAccounts()), ruleEnforcement.getCloudProvider());
    Set<String> allowedAccountIds = null;
    if (nextGenConnectorResponses != null) {
      allowedAccountIds = rbacHelper.checkAccountIdsGivenPermission(accountId, null, null,
          nextGenConnectorResponses.stream().map(ConnectorInfoDTO::getIdentifier).collect(Collectors.toSet()),
          RULE_EXECUTE);
    }
    if (allowedAccountIds == null || allowedAccountIds.size() != nextGenConnectorResponses.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, GOVERNANCE_CONNECTOR), WingsException.USER, null);
    }
    ruleEnforcement.setAccountId(accountId);
    ruleEnforcement.setRunCount(0);
    if (ruleEnforcement.getExecutionTimezone() == null) {
      ruleEnforcement.setExecutionTimezone("UTC");
    }

    try {
      CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(
          ruleEnforcement.getExecutionSchedule(), TimeZone.getTimeZone(ruleEnforcement.getExecutionTimezone()));
      CronSequenceGenerator.isValidExpression(String.valueOf(cronSequenceGenerator));
      // TO DO: Timezone and Cron validation needs to be moved to a non deprecated method
    } catch (Exception e) {
      throw new InvalidRequestException("cron is not valid");
    }
    if (ruleEnforcementService.listName(accountId, ruleEnforcement.getName(), true) != null) {
      throw new InvalidRequestException("Rule Enforcement with given name already exits");
    }
    List<String> targetRegions = ruleEnforcement.getTargetRegions();
    if (targetRegions != null) {
      targetRegions.removeAll(Collections.singleton(null));
    }
    ruleEnforcement.setTargetRegions(targetRegions);
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    ruleEnforcementService.checkLimitsAndValidate(ruleEnforcement, governanceConfig);
    ruleEnforcementService.save(ruleEnforcement);

    // Insert a record in dkron
    // TO DO: Add support for GCP cloud scheduler as well.
    if (configuration.getGovernanceConfig().isUseDkron()) {
      log.info("Use dkron is enabled in config");
      try {
        // This will be read by the enqueue api during callback
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(RULE_ENFORCEMENT_ID, ruleEnforcement.getUuid());
        Map<String, String> tags = new HashMap<>();
        tags.put(configuration.getGovernanceConfig().getTagsKey(), configuration.getGovernanceConfig().getTagsValue());

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ACCOUNT_ID, ruleEnforcement.getAccountId());
        metadata.put(EXECUTION_SCHEDULE_LOCAL, ruleEnforcement.getExecutionSchedule());
        metadata.put(ENFORCEMENT_ID, ruleEnforcement.getUuid());
        JSONArray headers = new JSONArray();
        headers.add(CONTENT_TYPE_APPLICATION_JSON);
        SchedulerDTO schedulerDTO =
            SchedulerDTO.builder()
                .tags(tags)
                .retries(2)
                .schedule(ruleEnforcement.getExecutionSchedule())
                .disabled(!ruleEnforcement.getIsEnabled())
                .name(ruleEnforcement.getUuid().toLowerCase())
                .displayname("ag_" + ruleEnforcement.getName() + "_" + ruleEnforcement.getUuid())
                .timezone(ruleEnforcement.getExecutionTimezone())
                .executor(SCHEDULER_EXECUTOR)
                .metadata(metadata)
                .executor_config(SchedulerDTO.ExecutorConfig.builder()
                                     .method(SCHEDULER_HTTP_METHOD)
                                     .timeout(SCHEDULER_HTTP_TIMEOUT)
                                     .debug(SCHEDULER_IS_DEBUG)
                                     .url(configuration.getGovernanceConfig().getCallbackApiEndpoint())
                                     .body(jsonObject.toString())
                                     .headers(headers.toString())
                                     .tlsNoVerifyPeer("true") // Skip verifying certs
                                     .build())
                .build();
        log.info(new Gson().toJson(schedulerDTO));
        okhttp3.RequestBody body =
            okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), new Gson().toJson(schedulerDTO));
        Response res = schedulerClient.createOrUpdateJob(body).execute();
        log.info(CODE_MESSAGE_BODY, res.code(), res.message(), res.body());
      } catch (Exception e) {
        log.error("Error in creating/updating job in dkron", e);
      }
    }

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_ENFORCEMENT_NAME, ruleEnforcement.getName());
    properties.put(CLOUD_PROVIDER, ruleEnforcement.getCloudProvider());
    properties.put(EXECUTION_SCHEDULE, ruleEnforcement.getExecutionSchedule());
    properties.put(EXECUTION_TIMEZONE, ruleEnforcement.getExecutionTimezone());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_ENFORCEMENT_CREATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleEnforcementCreateEvent(accountId, ruleEnforcement.toDTO()));
      return ruleEnforcementService.listName(accountId, ruleEnforcement.getName(), false);
    })));
  }

  @DELETE
  @Hidden
  @Path("enforcement/{enforcementID}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a rule", nickname = "deleteRuleEnforcement")
  @LogAccountIdentifier
  @Operation(operationId = "deleteRuleEnforcement", description = "Delete a Rule enforcement for the given a ID.",
      summary = "Delete a rule enforcement",
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
          required = true, description = "Unique identifier for the rule enforcement") @NotNull @Valid String uuid) {
    rbacHelper.checkRuleEnforcementDeletePermission(accountId, null, null);

    if (configuration.getGovernanceConfig().isUseDkron()) {
      log.info("Use dkron is enabled in config");
      try {
        String schedulerName = uuid.toLowerCase();
        Response res = schedulerClient.deleteJob(schedulerName).execute();
        log.info(CODE_MESSAGE_BODY, res.code(), res.message(), res.body());
      } catch (Exception e) {
        log.error("Error in deleting job from dkron", e);
      }
    }
    RuleEnforcement ruleEnforcement = ruleEnforcementService.listId(accountId, uuid, false);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_ENFORCEMENT_NAME, ruleEnforcement.getName());
    properties.put(CLOUD_PROVIDER, ruleEnforcement.getCloudProvider());
    properties.put(EXECUTION_SCHEDULE, ruleEnforcement.getExecutionSchedule());
    properties.put(EXECUTION_TIMEZONE, ruleEnforcement.getExecutionTimezone());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_ENFORCEMENT_DELETE, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleEnforcementDeleteEvent(accountId, ruleEnforcement.toDTO()));
      return ruleEnforcementService.delete(accountId, uuid);
    })));
  }

  @PUT
  @Hidden
  @Path("enforcement")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a Rule enforcement", nickname = "updateEnforcement")
  @LogAccountIdentifier
  @Operation(operationId = "updateEnforcement", description = "Update a Rule enforcement",
      summary = "Update a Rule enforcement",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update a existing Rule enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleEnforcement>
  update(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing rule enforcement object")
      @Valid CreateRuleEnforcementDTO createRuleEnforcementDTO) {
    rbacHelper.checkRuleEnforcementEditPermission(accountId, null, null);
    if (createRuleEnforcementDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleEnforcement ruleEnforcement = createRuleEnforcementDTO.getRuleEnforcement();
    ruleEnforcement.setAccountId(accountId);
    RuleEnforcement ruleEnforcementFromMongo =
        ruleEnforcementService.listId(accountId, ruleEnforcement.getUuid(), false);
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    ruleEnforcementService.checkLimitsAndValidate(ruleEnforcement, governanceConfig);
    Set<String> rules = new HashSet<>();
    if (ruleEnforcement.getRuleIds() != null && ruleEnforcement.getRuleIds().size() > 0) {
      governanceRuleService.check(accountId, ruleEnforcement.getRuleIds());
      rules.addAll(ruleEnforcement.getRuleIds());
    }
    if (ruleEnforcement.getRuleSetIDs() != null && ruleEnforcement.getRuleSetIDs().size() > 0) {
      ruleSetService.check(accountId, ruleEnforcement.getRuleSetIDs());
      List<RuleSet> ruleSets = ruleSetService.listPacks(accountId, ruleEnforcement.getRuleSetIDs());
      for (RuleSet ruleSet : ruleSets) {
        rules.addAll(ruleSet.getRulesIdentifier());
      }
    }
    if (rules.size() < 1) {
      throw new InvalidRequestException("At least one rule should be added in ruleIds/ruleSetIDs");
    }
    ruleSetService.validateCloudProvider(accountId, rules, ruleEnforcementFromMongo.getCloudProvider());
    if (ruleEnforcement.getRuleIds() != null) {
      Set<String> rulesPermitted = rbacHelper.checkRuleIdsGivenPermission(accountId, null, null, rules, RULE_EXECUTE);
      if (rulesPermitted.size() != rules.size()) {
        throw new NGAccessDeniedException(
            String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE),
            WingsException.USER, null);
      }
    }
    if (ruleEnforcement.getTargetAccounts() != null) {
      Set<ConnectorInfoDTO> nextGenConnectorResponses = governanceRuleService.getConnectorResponse(
          accountId, new HashSet<>(ruleEnforcement.getTargetAccounts()), ruleEnforcement.getCloudProvider());
      Set<String> allowedAccountIds = null;
      if (nextGenConnectorResponses != null) {
        allowedAccountIds = rbacHelper.checkAccountIdsGivenPermission(accountId, null, null,
            nextGenConnectorResponses.stream().map(ConnectorInfoDTO::getIdentifier).collect(Collectors.toSet()),
            RULE_EXECUTE);
      }
      if (allowedAccountIds == null || allowedAccountIds.size() != nextGenConnectorResponses.size()) {
        throw new NGAccessDeniedException(
            String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, GOVERNANCE_CONNECTOR), WingsException.USER, null);
      }
    }

    // Update dkron if enforcement is toggled or schedule is changed or timezone is changed
    if (configuration.getGovernanceConfig().isUseDkron()) {
      log.info("Use dkron is enabled in config.");
      try {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(RULE_ENFORCEMENT_ID, ruleEnforcementFromMongo.getUuid());
        Map<String, String> tags = new HashMap<>();
        tags.put(configuration.getGovernanceConfig().getTagsKey(), configuration.getGovernanceConfig().getTagsValue());
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ACCOUNT_ID, ruleEnforcement.getAccountId());
        metadata.put(EXECUTION_SCHEDULE_LOCAL, ruleEnforcement.getExecutionSchedule());
        metadata.put(ENFORCEMENT_ID, ruleEnforcement.getUuid());
        JSONArray headers = new JSONArray();
        headers.add(CONTENT_TYPE_APPLICATION_JSON);
        SchedulerDTO schedulerDTO =
            SchedulerDTO.builder()
                .tags(tags)
                .retries(2)
                .schedule(ruleEnforcement.getExecutionSchedule()) // This can be updated by user
                .disabled(!ruleEnforcement.getIsEnabled()) // This can be updated by user
                .name(ruleEnforcementFromMongo.getUuid().toLowerCase())
                .displayname("ag_" + ruleEnforcementFromMongo.getName() + "_" + ruleEnforcementFromMongo.getUuid())
                .timezone(ruleEnforcement.getExecutionTimezone()) // This can be updated by user
                .executor(SCHEDULER_EXECUTOR)
                .metadata(metadata)
                .executor_config(SchedulerDTO.ExecutorConfig.builder()
                                     .method(SCHEDULER_HTTP_METHOD)
                                     .timeout(SCHEDULER_HTTP_TIMEOUT)
                                     .debug(SCHEDULER_IS_DEBUG)
                                     .url(configuration.getGovernanceConfig().getCallbackApiEndpoint())
                                     .body(jsonObject.toString())
                                     .headers(headers.toString())
                                     .tlsNoVerifyPeer("true") // Skip verifying certs
                                     .build())
                .build();
        log.info(new Gson().toJson(schedulerDTO));
        okhttp3.RequestBody body =
            okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), new Gson().toJson(schedulerDTO));
        Response res = schedulerClient.createOrUpdateJob(body).execute();
        log.info(CODE_MESSAGE_BODY, res.code(), res.message(), res.body());
      } catch (Exception e) {
        log.error("Error in creating/updating job in dkron", e);
      }
    }

    ruleEnforcementService.update(ruleEnforcement);
    RuleEnforcement updatedRuleEnforcement = ruleEnforcementService.listId(accountId, ruleEnforcement.getUuid(), false);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_ENFORCEMENT_NAME, updatedRuleEnforcement.getName());
    properties.put(CLOUD_PROVIDER, updatedRuleEnforcement.getCloudProvider());
    properties.put(EXECUTION_SCHEDULE, updatedRuleEnforcement.getExecutionSchedule());
    properties.put(EXECUTION_TIMEZONE, updatedRuleEnforcement.getExecutionTimezone());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_ENFORCEMENT_UPDATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(
          new RuleEnforcementUpdateEvent(accountId, updatedRuleEnforcement.toDTO(), ruleEnforcementFromMongo.toDTO()));
      return updatedRuleEnforcement;
    })));
  }
  // TO DO add filter support
  @POST
  @Path("enforcement/list")
  @ApiOperation(value = "Get enforcement list", nickname = "getRuleEnforcement")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getRuleEnforcement", description = "Fetch Rule Enforcement ",
      summary = "Fetch Rule Enforcement for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of rules  Enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<RuleEnforcement>>
  listRuleEnforcements(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing  Rule Enforcement  object") @Valid
      @NotNull CreateRuleEnforcementDTO createRuleEnforcementDTO) {
    rbacHelper.checkRuleEnforcementViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(ruleEnforcementService.list(accountId));
  }
  // TO DO list rule information
  @POST
  @Path("enforcement/count")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get enforcement count", nickname = "getRuleEnforcementCount")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getRuleEnforcementCount", description = "Fetch Rule Enforcement count",
      summary = "Fetch Rule Enforcement count for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of rules  Enforcement",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<EnforcementCount>
  enforcementCount(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing  Rule Enforcement count object")
      @Valid EnforcementCountDTO enforcementCountDTO) {
    if (enforcementCountDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    EnforcementCountRequest enforcementCountRequest = enforcementCountDTO.getEnforcementCountRequest();
    log.info("{}", enforcementCountRequest);
    return ResponseDTO.newResponse(ruleEnforcementService.getCount(accountId, enforcementCountRequest));
  }

  @POST
  @Path("execution/details")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "execution Detail", nickname = "getExecutionDetail")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getExecutionDetail", description = "execution Detail",
      summary = "Fetch Rule Enforcement count for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns execution Details", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<ExecutionDetails>
  executionDetail(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing  Rule Enforcement count object")
      @Valid ExecutionDetailDTO executionDetailDTO) {
    if (executionDetailDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    ExecutionDetailRequest executionDetailRequest = executionDetailDTO.getExecutionDetailRequest();
    log.info("{}", executionDetailRequest);
    return ResponseDTO.newResponse(ruleEnforcementService.getDetails(accountId, executionDetailRequest));
  }
}