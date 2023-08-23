/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.CLOUD_PROVIDER;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_SET_CREATED;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_SET_DELETE;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_SET_UPDATED;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.TelemetryConstants.RULE_SET_NAME;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_EXECUTE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.RuleSetCreateEvent;
import io.harness.ccm.audittrails.events.RuleSetDeleteEvent;
import io.harness.ccm.audittrails.events.RuleSetUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreateRuleSetDTO;
import io.harness.ccm.views.dto.CreateRuleSetFilterDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.helper.RuleSetList;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.GovernanceConfig;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("governance")
@Path("governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Service
@OwnedBy(CE)
@Slf4j
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

public class GovernanceRuleSetResource {
  public static final String ACCOUNT_ID = "accountId";
  private final CCMRbacHelper rbacHelper;
  private final RuleSetService ruleSetService;
  private final GovernanceRuleService ruleService;
  private final TelemetryReporter telemetryReporter;
  private final CENextGenConfiguration configuration;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryRule = DEFAULT_RETRY_POLICY;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  public static final String MALFORMED_ERROR = "Request payload is malformed";

  @Inject
  public GovernanceRuleSetResource(RuleSetService ruleSetService, GovernanceRuleService ruleService,
      TelemetryReporter telemetryReporter, OutboxService outboxService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, CENextGenConfiguration configuration,
      CCMRbacHelper rbacHelper) {
    this.rbacHelper = rbacHelper;
    this.ruleSetService = ruleSetService;
    this.ruleService = ruleService;
    this.telemetryReporter = telemetryReporter;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.configuration = configuration;
  }

  @POST
  @Path("ruleSet")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new rule Set", nickname = "addRuleSet")
  @Operation(operationId = "addRuleSet", summary = "Add a rule Set ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created rule Set")
      })
  public ResponseDTO<RuleSet>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing rule Set object") @Valid CreateRuleSetDTO createRuleSetDTO) {
    rbacHelper.checkRuleSetEditPermission(accountId, null, null);
    if (createRuleSetDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }

    RuleSet ruleSet = createRuleSetDTO.getRuleSet();
    if (ruleSetService.fetchByName(accountId, ruleSet.getName(), true) != null) {
      throw new InvalidRequestException("Rule Set with this name already exits");
    }
    if (!ruleSet.getIsOOTB()) {
      ruleSet.setAccountId(accountId);
      ruleService.check(accountId, ruleSet.getRulesIdentifier());
    } else if (ruleSet.getAccountId().equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      ruleSet.setAccountId(GLOBAL_ACCOUNT_ID);
      ruleService.check(GLOBAL_ACCOUNT_ID, ruleSet.getRulesIdentifier());
    } else {
      throw new InvalidRequestException("Not authorised to create OOTB rule set. Make a custom rule set instead");
    }
    if (ruleSet.getRulesIdentifier() == null || ruleSet.getRulesIdentifier().size() < 1) {
      throw new InvalidRequestException("rulesIdentifier is a required field.");
    }
    Set<String> uniqueRuleIds = new HashSet<>();
    uniqueRuleIds.addAll(ruleSet.getRulesIdentifier());
    if (ruleSet.getCloudProvider() == null) {
      ruleSet.setCloudProvider(
          ruleService.fetchById(accountId, uniqueRuleIds.iterator().next(), false).getCloudProvider());
    }
    ruleSetService.validateCloudProvider(accountId, uniqueRuleIds, ruleSet.getCloudProvider());
    Set<String> rulesPermitted =
        rbacHelper.checkRuleIdsGivenPermission(accountId, null, null, uniqueRuleIds, RULE_EXECUTE);
    if (rulesPermitted.size() != uniqueRuleIds.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE),
          WingsException.USER, null);
    }
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    if (ruleSet.getRulesIdentifier().size() > governanceConfig.getPoliciesInPack()) {
      throw new InvalidRequestException("Limit of Rules in a set is exceeded ");
    }
    ruleSet.setUuid(null);
    ruleSetService.save(ruleSet);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_SET_NAME, ruleSet.getName());
    properties.put(CLOUD_PROVIDER, ruleSet.getCloudProvider());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_SET_CREATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleSetCreateEvent(accountId, ruleSet.toDTO()));
      return ruleSetService.fetchByName(accountId, ruleSet.getName(), false);
    })));
  }

  @PUT
  @Path("ruleSet")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update an existing rule pack", nickname = "updateRuleSet")
  @LogAccountIdentifier
  @Operation(operationId = "updateRuleSet", description = "Update a Rule set", summary = "Update a Rule set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "update a existing Rule pack", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleSet>
  updateRuleSet(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Rule pack object") @Valid CreateRuleSetDTO createRuleSetDTO) {
    rbacHelper.checkRuleSetEditPermission(accountId, null, null);
    if (createRuleSetDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleSet ruleSet = createRuleSetDTO.getRuleSet();
    ruleSet.toDTO();
    ruleSet.setAccountId(accountId);
    RuleSet oldRuleSet = ruleSetService.fetchById(accountId, ruleSet.getUuid(), true);
    if (oldRuleSet.getIsOOTB()) {
      throw new InvalidRequestException("Editing OOTB Rule Set is not allowed");
    }
    ruleService.check(accountId, ruleSet.getRulesIdentifier());
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    if (ruleSet.getRulesIdentifier().size() > governanceConfig.getPoliciesInPack()) {
      throw new InvalidRequestException("Limit of Rules in a set is exceeded ");
    }
    if (ruleSet.getRulesIdentifier() == null || ruleSet.getRulesIdentifier().size() < 1) {
      throw new InvalidRequestException("rulesIdentifier is a required field.");
    }
    Set<String> uniqueRuleIds = new HashSet<>();
    uniqueRuleIds.addAll(ruleSet.getRulesIdentifier());
    ruleSetService.validateCloudProvider(accountId, uniqueRuleIds, oldRuleSet.getCloudProvider());
    Set<String> rulesPermitted =
        rbacHelper.checkRuleIdsGivenPermission(accountId, null, null, uniqueRuleIds, RULE_EXECUTE);
    if (rulesPermitted.size() != uniqueRuleIds.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, RULE_EXECUTE, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE),
          WingsException.USER, null);
    }
    ruleSetService.update(accountId, ruleSet);
    RuleSet updatedRuleSet = ruleSetService.fetchById(accountId, ruleSet.getUuid(), false);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_SET_NAME, updatedRuleSet.getName());
    properties.put(CLOUD_PROVIDER, updatedRuleSet.getCloudProvider());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_SET_UPDATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleSetUpdateEvent(accountId, updatedRuleSet.toDTO(), oldRuleSet.toDTO()));
      return updatedRuleSet;
    })));
  }

  @PUT
  @Hidden
  @InternalApi
  @Path("ruleSetOOTB")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update an existing rule set", nickname = "updateOOTBRuleSet", hidden = true)
  @LogAccountIdentifier
  @Operation(operationId = "updateOOTBRuleSet", description = "Update a  OOTB Rule Pack",
      summary = "Update a  OOTB Rule Pack",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update an existing OOTB Rule pack",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleSet>
  updateRuleSetOOTB(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Rule pack object") @Valid CreateRuleSetDTO createRuleSetDTO) {
    if (createRuleSetDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleSet ruleSet = createRuleSetDTO.getRuleSet();
    ruleSet.toDTO();
    if (!ruleSet.getAccountId().equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Editing OOTB rule set is not allowed");
    }
    ruleSetService.fetchById(GLOBAL_ACCOUNT_ID, ruleSet.getUuid(), false);
    if (ruleSet.getRulesIdentifier().size() > configuration.getGovernanceConfig().getPoliciesInPack()) {
      throw new InvalidRequestException("Limit of Rules In a Set is exceeded ");
    }
    ruleService.check(GLOBAL_ACCOUNT_ID, ruleSet.getRulesIdentifier());
    ruleSetService.update(GLOBAL_ACCOUNT_ID, ruleSet);
    return ResponseDTO.newResponse(ruleSetService.fetchById(GLOBAL_ACCOUNT_ID, ruleSet.getUuid(), false));
  }

  @POST
  @Path("ruleSet/list/{id}")
  @ApiOperation(value = "Get rules for pack", nickname = "getRuleSet")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getRuleSet", description = "Fetch rules packs ", summary = "Fetch rule packs for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns List of rule packs", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<Rule>>
  listRule(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing rule packs object") @Valid CreateRuleSetDTO createRuleSetDTO,
      @PathParam("id") @Parameter(
          required = true, description = "Unique identifier for the rule packs") @NotNull @Valid String uuid) {
    rbacHelper.checkRuleSetViewPermission(accountId, null, null);
    RuleSet query = createRuleSetDTO.getRuleSet();
    List<Rule> rules = new ArrayList<>();
    ruleSetService.fetchByName(accountId, query.getName(), false);
    ruleService.check(accountId, query.getRulesIdentifier());

    return ResponseDTO.newResponse(rules);
  }

  @POST
  @Path("ruleSet/list")
  @ApiOperation(value = "list all rule packs", nickname = "listRuleSets")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "listRuleSets", description = "list Rule Packs ", summary = "Fetch rule packs for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns List of rule packs", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleSetList>
  listRuleSet(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing rule pack object")
      @Valid CreateRuleSetFilterDTO createRuleSetFilterDTO) {
    rbacHelper.checkRuleSetViewPermission(accountId, null, null);
    if (createRuleSetFilterDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleSetFilter ruleSet = createRuleSetFilterDTO.getRuleSetFilter();
    return ResponseDTO.newResponse(ruleSetService.list(accountId, ruleSet));
  }

  @DELETE
  @Path("ruleSet/{ruleSetId}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete a rule set", nickname = "deleteRuleSet")
  @LogAccountIdentifier
  @Operation(operationId = "deleteRuleSet", description = "Delete a Rule set for the given a ID.",
      summary = "Delete a rule set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleSetId") @Parameter(
          required = true, description = "Unique identifier for the rule") @NotNull @Valid String uuid) {
    rbacHelper.checkRuleSetDeletePermission(accountId, null, null);
    RuleSet ruleSet = ruleSetService.fetchById(accountId, uuid, false);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_SET_NAME, ruleSet.getName());
    properties.put(CLOUD_PROVIDER, ruleSet.getCloudProvider());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_SET_DELETE, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleSetDeleteEvent(accountId, ruleSet.toDTO()));
      return ruleSetService.delete(accountId, uuid);
    })));
  }

  @DELETE
  @Hidden
  @InternalApi
  @Path("ruleSetOOTB/{ruleSetId}")
  @Timed
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "delete OOTB Rule Pack", nickname = "deleteOOTBRuleSet", hidden = true)
  @LogAccountIdentifier
  @Operation(operationId = "deleteOOTBRuleSet", description = "delete OOTB Rule Pack for the given a ID.",
      summary = "delete OOTB Rule Pack",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  deleteOOTB(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleSetId") @Parameter(
          required = true, description = "Unique identifier for the rule") @NotNull @Valid String uuid) {
    if (!accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Editing OOTB rule pack is not allowed");
    }
    boolean result = ruleSetService.deleteOOTB(accountId, uuid);
    return ResponseDTO.newResponse(result);
  }
}
