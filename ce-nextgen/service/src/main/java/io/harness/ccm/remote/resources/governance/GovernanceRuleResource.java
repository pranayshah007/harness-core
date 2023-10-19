/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.CLOUD_PROVIDER;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_CREATED;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_DELETE;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_RULE_UPDATED;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.TelemetryConstants.RESOURCE_TYPE;
import static io.harness.ccm.TelemetryConstants.RULE_NAME;
import static io.harness.ccm.rbac.CCMRbacPermissions.CONNECTOR_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_EXECUTE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.governance.entities.RecommendationAdhocDTO;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.service.intf.CCMConnectorDetailsService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dao.RuleEnforcementDAO;
import io.harness.ccm.views.dto.CloneRuleDTO;
import io.harness.ccm.views.dto.CreateRuleDTO;
import io.harness.ccm.views.dto.GovernanceAdhocEnqueueDTO;
import io.harness.ccm.views.dto.GovernanceEnqueueResponseDTO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.dto.ListDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleClone;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.helper.RuleStoreType;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.GovernanceConfig;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.YamlSchemaValidator;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
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

@Slf4j
@Service
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Tag(name = "Rule", description = "This contains APIs related to Rule Management ")
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

public class GovernanceRuleResource {
  private final GovernanceRuleService governanceRuleService;
  private final RuleSetService ruleSetService;
  private final RuleEnforcementService ruleEnforcementService;
  private final CCMRbacHelper rbacHelper;
  private final ConnectorResourceClient connectorResourceClient;
  private final RuleExecutionService ruleExecutionService;
  private final TelemetryReporter telemetryReporter;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final CENextGenConfiguration configuration;
  @Inject private YamlSchemaProvider yamlSchemaProvider;
  @Inject private YamlSchemaValidator yamlSchemaValidator;
  @Inject private RuleEnforcementDAO ruleEnforcementDAO;
  @Inject CCMConnectorDetailsService connectorDetailsService;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  public static final String MALFORMED_ERROR = "Request payload is malformed";
  private static final RetryPolicy<Object> transactionRetryRule = DEFAULT_RETRY_POLICY;

  @Inject
  public GovernanceRuleResource(GovernanceRuleService governanceRuleService,
      RuleEnforcementService ruleEnforcementService, RuleSetService ruleSetService,
      ConnectorResourceClient connectorResourceClient, RuleExecutionService ruleExecutionService,
      TelemetryReporter telemetryReporter, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      OutboxService outboxService, YamlSchemaProvider yamlSchemaProvider, YamlSchemaValidator yamlSchemaValidator,
      CENextGenConfiguration configuration, CCMRbacHelper rbacHelper) {
    this.governanceRuleService = governanceRuleService;
    this.rbacHelper = rbacHelper;
    this.ruleEnforcementService = ruleEnforcementService;
    this.ruleSetService = ruleSetService;
    this.connectorResourceClient = connectorResourceClient;
    this.ruleExecutionService = ruleExecutionService;
    this.telemetryReporter = telemetryReporter;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.configuration = configuration;
  }

  @PublicApi
  @POST
  @Path("enqueue")
  @Timed
  @Hidden
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Enqueues job for execution", nickname = "enqueueGovernanceJob", hidden = true)
  // TO DO: Also check with PL team as this does not require accountId to be passed, how to add accountId in the log
  // context here ?
  @Operation(operationId = "enqueueGovernanceJob", description = "Enqueues job for execution.",
      summary = "Enqueues job for execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns success when job is enqueued",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernanceEnqueueResponseDTO>
  enqueue(@Parameter(required = false, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountId,
      @RequestBody(required = true, description = "Request body for queuing the governance job")
      @Valid GovernanceJobEnqueueDTO governanceJobEnqueueDTO) throws IOException {
    // TO DO: Refactor and make this method smaller
    // Step-1 Fetch from mongo
    String ruleEnforcementUuid = governanceJobEnqueueDTO.getRuleEnforcementId();
    List<String> enqueuedRuleExecutionIds = new ArrayList<>();
    if (ruleEnforcementUuid != null) {
      // Call is from dkron
      log.info("Rule enforcement config id is {}", ruleEnforcementUuid);
      RuleEnforcement ruleEnforcement = ruleEnforcementService.get(ruleEnforcementUuid);
      if (ruleEnforcement == null) {
        log.error(
            "For rule enforcement setting {}: not found in db. Skipping enqueuing in faktory", ruleEnforcementUuid);
        return ResponseDTO.newResponse(GovernanceEnqueueResponseDTO.builder().ruleExecutionId(null).build());
      }
      RuleEnforcement ruleEnforcementUpdate = RuleEnforcement.builder()
                                                  .accountId(ruleEnforcement.getAccountId())
                                                  .uuid(ruleEnforcementUuid)
                                                  .runCount(ruleEnforcement.getRunCount() + 1)
                                                  .build();
      log.info("ruleEnforcementUpdate count{}", ruleEnforcementUpdate.getRunCount());
      ruleEnforcementDAO.updateCount(ruleEnforcementUpdate);
      RuleCloudProviderType ruleCloudProviderType = ruleEnforcement.getCloudProvider();
      accountId = ruleEnforcement.getAccountId();
      if (ruleCloudProviderType != RuleCloudProviderType.AWS && ruleCloudProviderType != RuleCloudProviderType.AZURE) {
        log.error("Support for non AWS/AZURE cloud providers is not present atm. Skipping enqueuing in faktory");
        // TO DO: Return simple response to dkron instead of empty for debugging purposes
        return ResponseDTO.newResponse();
      }

      if (ruleEnforcement.getTargetAccounts() == null || ruleEnforcement.getTargetAccounts().size() == 0) {
        log.error("For rule enforcement setting {}: need at least one target cloud accountId to work on. "
                + "Skipping enqueuing in faktory",
            ruleEnforcementUuid);
        return ResponseDTO.newResponse(GovernanceEnqueueResponseDTO.builder().ruleExecutionId(null).build());
      }

      // Step-2 Prep unique rule Ids set from this enforcement
      Set<String> uniqueRuleIds = new HashSet<>();
      if (ruleEnforcement.getRuleIds() != null && ruleEnforcement.getRuleIds().size() > 0) {
        // Assumption: The ruleIds in the enforcement records are all valid ones
        uniqueRuleIds.addAll(ruleEnforcement.getRuleIds());
      }
      if (ruleEnforcement.getRuleSetIDs() != null && ruleEnforcement.getRuleSetIDs().size() > 0) {
        List<RuleSet> ruleSets = ruleSetService.listPacks(accountId, ruleEnforcement.getRuleSetIDs());
        for (RuleSet ruleSet : ruleSets) {
          uniqueRuleIds.addAll(ruleSet.getRulesIdentifier());
        }
      }
      log.info("For rule enforcement setting {}: uniqueRuleIds: {}", ruleEnforcementUuid, uniqueRuleIds);
      List<Rule> rulesList = governanceRuleService.list(accountId, new ArrayList<>(uniqueRuleIds));
      if (rulesList == null) {
        log.error("For rule enforcement setting {}: no rules exists in mongo. Nothing to enqueue", ruleEnforcementUuid);
        return ResponseDTO.newResponse(GovernanceEnqueueResponseDTO.builder().ruleExecutionId(null).build());
      }
      // Step-3 Figure out roleArn and externalId from the connector listv2 api call for all target accounts.
      Set<ConnectorInfoDTO> nextGenConnectorResponses = governanceRuleService.getConnectorResponse(
          accountId, new HashSet<>(ruleEnforcement.getTargetAccounts()), ruleEnforcement.getCloudProvider());
      log.info(
          "For rule enforcement setting {}: Got connector data: {}", ruleEnforcementUuid, nextGenConnectorResponses);

      // Step-4 Enqueue in faktory
      for (ConnectorInfoDTO connectorInfoDTO : nextGenConnectorResponses) {
        String faktoryJobType = configuration.getGovernanceConfig().getAwsFaktoryJobType();
        String faktoryQueueName = configuration.getGovernanceConfig().getAwsFaktoryQueueName();
        if (ruleEnforcement.getCloudProvider() == RuleCloudProviderType.AZURE) {
          faktoryJobType = configuration.getGovernanceConfig().getAzureFaktoryJobType();
          faktoryQueueName = configuration.getGovernanceConfig().getAzureFaktoryQueueName();
        }
        List<RuleExecution> ruleExecutions = governanceRuleService.enqueue(accountId, ruleEnforcement, rulesList,
            connectorInfoDTO.getConnectorConfig(), connectorInfoDTO.getIdentifier(), faktoryJobType, faktoryQueueName);
        enqueuedRuleExecutionIds.addAll(ruleExecutionService.save(ruleExecutions));
      }
    }
    return ResponseDTO.newResponse(
        GovernanceEnqueueResponseDTO.builder().ruleExecutionId(enqueuedRuleExecutionIds).build());
  }

  @NextGenManagerAuth
  @POST
  @Hidden
  @Path("rule")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new rule ", nickname = "CreateNewRule")
  @Operation(operationId = "CreateNewRule", summary = "Add a new rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created rule")
      })
  public ResponseDTO<Rule>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing Rule object") @Valid CreateRuleDTO createRuleDTO) {
    rbacHelper.checkRuleEditPermission(accountId, null, null);
    if (createRuleDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    Rule rule = createRuleDTO.getRule();
    if (rule.getCloudProvider() == null) {
      throw new InvalidRequestException("cloudProvider is a required field.");
    }
    if (!rule.getIsOOTB()) {
      rule.setAccountId(accountId);
    } else if (accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      if (rule.getAccountId() == null) {
        rule.setAccountId(GLOBAL_ACCOUNT_ID);
      } else {
        rule.setAccountId(rule.getAccountId());
      }
    } else {
      throw new InvalidRequestException("Not authorised to create OOTB rules. Make a custom rule instead");
    }
    if (governanceRuleService.fetchByName(accountId, rule.getName(), true) != null) {
      throw new InvalidRequestException("Rule with the given name already exits");
    }
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    RuleList ruleList = governanceRuleService.list(governancePolicyFilter);
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    if (ruleList.getRules().size() >= governanceConfig.getPolicyPerAccountLimit()) {
      throw new InvalidRequestException("You have exceeded the limit for rules creation");
    }
    // TO DO: Handle this for custom rules and git connectors
    rule.setStoreType(RuleStoreType.INLINE);
    rule.setVersionLabel("0.0.1");
    rule.setDeleted(false);
    rule.setResourceType(governanceRuleService.getResourceType(rule.getRulesYaml()));
    rule.setUuid(null);
    if (rule.getIsOOTB() && rule.getForRecommendation()
        && accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      rule.setForRecommendation(true);
    } else {
      rule.setForRecommendation(false);
    }
    governanceRuleService.custodianValidate(rule);
    governanceRuleService.save(rule);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_NAME, rule.getName());
    properties.put(CLOUD_PROVIDER, rule.getCloudProvider());
    properties.put(RESOURCE_TYPE, rule.getResourceType());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_CREATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(

        Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new RuleCreateEvent(accountId, rule.toDTO()));
          return governanceRuleService.fetchByName(accountId, rule.getName(), false);
        })));
  }

  @NextGenManagerAuth
  @POST
  @Path("ruleClone")
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "Clone a rule", nickname = "CloneRule")
  @LogAccountIdentifier
  @Operation(operationId = "CloneRule", description = "Clone a Rule with the given ID.", summary = "Clone a rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "newly created rule", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Rule>
  clone(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing Rule uuid") @Valid CloneRuleDTO cloneRuleDTO) {
    if (cloneRuleDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleClone ruleClone = cloneRuleDTO.getRuleClone();
    Rule existingRule = governanceRuleService.fetchById(accountId, ruleClone.getUuid(), false);
    Rule newRule = Rule.builder().build();
    newRule.setIsOOTB(false);
    newRule.setName(existingRule.getName() + "-clone");
    if (governanceRuleService.fetchByName(accountId, newRule.getName(), true) != null) {
      throw new InvalidRequestException("A clone with the given name already exists");
    }
    newRule.setCloudProvider(existingRule.getCloudProvider());
    newRule.setRulesYaml(existingRule.getRulesYaml());
    newRule.setDescription(existingRule.getDescription());
    newRule.setTags(existingRule.getTags());
    CreateRuleDTO createRuleDTO = CreateRuleDTO.builder().rule(newRule).build();
    return create(accountId, createRuleDTO);
  }
  @NextGenManagerAuth
  @PUT
  @Path("rule")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a existing Rule", nickname = "updateRule")
  @LogAccountIdentifier
  @Operation(operationId = "updateRule", description = "Update a Rule", summary = "Update a Rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "update an existing Rule", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Rule>
  updateRule(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing rule object") @Valid CreateRuleDTO createRuleDTO) {
    rbacHelper.checkRuleEditPermission(accountId, null, null);
    if (createRuleDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    Rule rule = createRuleDTO.getRule();
    Rule oldRule = governanceRuleService.fetchById(accountId, rule.getUuid(), true);
    if (oldRule.getIsOOTB()) {
      throw new InvalidRequestException("Editing OOTB rule is not allowed");
    }
    if (rule.getRulesYaml() != null) {
      Rule testSchema = Rule.builder().build();
      testSchema.setName(oldRule.getName());
      testSchema.setRulesYaml(rule.getRulesYaml());
      governanceRuleService.custodianValidate(testSchema);
      rule.setResourceType(governanceRuleService.getResourceType(rule.getRulesYaml()));
    }
    rule.setForRecommendation(false);
    governanceRuleService.update(rule, accountId);
    Rule updatedRule = governanceRuleService.fetchById(accountId, rule.getUuid(), true);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_NAME, updatedRule.getName());
    properties.put(CLOUD_PROVIDER, updatedRule.getCloudProvider());
    properties.put(RESOURCE_TYPE, updatedRule.getResourceType());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_UPDATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleUpdateEvent(accountId, updatedRule.toDTO(), oldRule.toDTO()));
      return updatedRule;
    })));
  }
  @NextGenManagerAuth
  @PUT
  @Hidden
  @InternalApi
  @Path("ruleOOTB")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a existing OOTB Rule", nickname = "updateOOTBRule", hidden = true)
  @LogAccountIdentifier
  @Operation(operationId = "updateOOTBRule", description = "Update a OOTB Rule", summary = "Update a OOTB Rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "update an existing OOTB Rule",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Rule>
  updateRuleOOTB(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing rule object") @Valid CreateRuleDTO createRuleDTO) {
    if (createRuleDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }

    Rule rule = createRuleDTO.getRule();
    if (!accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Editing OOTB rule is not allowed");
    }
    String updateAccountId = rule.getAccountId() == null ? GLOBAL_ACCOUNT_ID : rule.getAccountId();
    Rule oldRule = governanceRuleService.fetchById(updateAccountId, rule.getUuid(), true);
    if (rule.getRulesYaml() != null) {
      Rule testSchema = Rule.builder().build();
      testSchema.setName(oldRule.getName());
      testSchema.setRulesYaml(rule.getRulesYaml());
      governanceRuleService.custodianValidate(testSchema);
      rule.setResourceType(governanceRuleService.getResourceType(rule.getRulesYaml()));
    }
    return ResponseDTO.newResponse(governanceRuleService.update(rule, updateAccountId));
  }
  // Internal API for deletion of OOTB rules
  @NextGenManagerAuth
  @DELETE
  @Path("{ruleID}")
  @Timed
  @Hidden
  @InternalApi
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "Delete a OOTB rule", nickname = "deleteOOTBRule", hidden = true)
  @LogAccountIdentifier
  @Operation(operationId = "deleteOOTBRule", description = "Delete an OOTB Rule for the given a ID.",
      summary = "Delete an OOTB Rule for the given a ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  deleteOOTB(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleID") @Parameter(
          required = true, description = "Unique identifier for the rule") @NotNull @Valid String uuid,
      @QueryParam("customAccountId") @Parameter(
          description = "Custom rule account identifier") String customAccountId) {
    if (!accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Deleting OOTB rule is not allowed");
    }
    String deleteRuleAccountId = customAccountId == null ? GLOBAL_ACCOUNT_ID : customAccountId;
    governanceRuleService.fetchById(deleteRuleAccountId, uuid, false);
    boolean result = governanceRuleService.delete(deleteRuleAccountId, uuid);
    return ResponseDTO.newResponse(result);
  }
  @NextGenManagerAuth
  @DELETE
  @Path("rule/{ruleID}")
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "Delete a rule", nickname = "deleteRule")
  @LogAccountIdentifier
  @Operation(operationId = "deleteRule", description = "Delete a Rule for the given a ID.", summary = "Delete a rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleID") @Parameter(
          required = true, description = "Unique identifier for the rule") @NotNull @Valid String uuid) {
    rbacHelper.checkRuleDeletePermission(accountId, null, null);
    HashMap<String, Object> properties = new HashMap<>();
    Rule rule = governanceRuleService.fetchById(accountId, uuid, false);
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_NAME, rule.getName());
    properties.put(CLOUD_PROVIDER, rule.getCloudProvider());
    properties.put(RESOURCE_TYPE, rule.getResourceType());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_DELETE, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleDeleteEvent(accountId, rule.toDTO()));
      return governanceRuleService.delete(accountId, uuid);
    })));
  }
  @NextGenManagerAuth
  @POST
  @Path("rule/list")
  @ApiOperation(value = "Get rules for given account", nickname = "getPolicies")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPolicies", description = "Fetch rules ", summary = "Fetch rules for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Returns List of rules", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleList>
  listRule(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing rule object") @Valid ListDTO listDTO,
      @Parameter(description = "Search by Rule name pattern") @QueryParam("RuleNamePattern") String ruleNamePattern) {
    rbacHelper.checkRuleViewPermission(accountId, null, null);
    GovernanceRuleFilter query;
    if (listDTO == null) {
      query = GovernanceRuleFilter.builder().build();
    } else {
      query = listDTO.getGovernanceRuleFilter();
    }
    if (ruleNamePattern != null) {
      query.setSearch(ruleNamePattern);
    }
    query.setAccountId(accountId);
    return ResponseDTO.newResponse(governanceRuleService.list(query));
  }
  @GET
  @Path("connectorList")
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "governanceConnectorList", nickname = "governanceConnectorList")
  @LogAccountIdentifier
  @Operation(operationId = "governanceConnectorList",
      description = "get connectors with governance enabled and valid permission",
      summary = "connectors with governance enabled and valid permission",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "newly created rule", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public List<ConnectorResponseDTO>
  listV2(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(description = "View governance connector list") @QueryParam("view") boolean view,
      @QueryParam("connectorType") ConnectorType connectorType) {
    List<ConnectorType> connectorTypes = new ArrayList<>();
    if (connectorType == null) {
      connectorTypes.add(ConnectorType.CE_AWS);
      connectorTypes.add(ConnectorType.CE_AZURE);
    } else {
      connectorTypes.add(connectorType);
    }
    List<ConnectorResponseDTO> nextGenConnectorResponses =
        connectorDetailsService.listNgConnectors(accountId, connectorTypes, Arrays.asList(CEFeatures.GOVERNANCE), null);
    Set<String> allowedAccountIds = null;
    List<ConnectorResponseDTO> connectorResponse = new ArrayList<>();
    if (nextGenConnectorResponses != null) {
      String permissionCheck = RULE_EXECUTE;
      if (view) {
        permissionCheck = CONNECTOR_VIEW;
      }
      allowedAccountIds = rbacHelper.checkAccountIdsGivenPermission(accountId, null, null,
          nextGenConnectorResponses.stream().map(e -> e.getConnector().getIdentifier()).collect(Collectors.toSet()),
          permissionCheck);
      log.info("Allowed AccountIds {}", allowedAccountIds);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        if (allowedAccountIds.contains(connector.getConnector().getIdentifier())) {
          connectorResponse.add(connector);
        }
      }
    }
    return connectorResponse;
  }

  @NextGenManagerAuth
  @POST
  @Path("enqueueAdhoc")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Enqueues job for execution", nickname = "enqueueAdhocGovernanceJob")
  // TO DO: Also check with PL team as this does not require accountId to be passed, how to add accountId in the log
  // context here ?
  @Operation(operationId = "enqueueGovernanceJob", description = "Enqueues job for execution.",
      summary = "Enqueues job for execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns success when job is enqueued",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernanceEnqueueResponseDTO>
  enqueueAdhoc(@Parameter(required = false, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountId,
      @RequestBody(required = true, description = "Request body for queuing the governance job")
      @Valid GovernanceAdhocEnqueueDTO governanceAdhocEnqueueDTO) throws IOException {
    List<String> ruleExecutionId = new ArrayList<>();
    rbacHelper.checkRuleExecutePermission(accountId, null, null, governanceAdhocEnqueueDTO.getRuleId());
    for (String targetAccount : governanceAdhocEnqueueDTO.getTargetAccountDetails().keySet()) {
      RecommendationAdhocDTO recommendationAdhocDTO =
          governanceAdhocEnqueueDTO.getTargetAccountDetails().get(targetAccount);
      rbacHelper.checkAccountExecutePermission(accountId, null, null, recommendationAdhocDTO.getCloudConnectorId());
      for (String targetRegion : governanceAdhocEnqueueDTO.getTargetRegions()) {
        GovernanceJobEnqueueDTO governanceJobEnqueueDTO =
            GovernanceJobEnqueueDTO.builder()
                .targetRegion(targetRegion)
                .targetAccountDetails(recommendationAdhocDTO)
                .ruleId(governanceAdhocEnqueueDTO.getRuleId())
                .isDryRun(governanceAdhocEnqueueDTO.getIsDryRun())
                .policy(governanceAdhocEnqueueDTO.getPolicy())
                .ruleCloudProviderType(governanceAdhocEnqueueDTO.getRuleCloudProviderType())
                .executionType(RuleExecutionType.EXTERNAL)
                .build();
        log.info("enqueued: {}", governanceJobEnqueueDTO);
        ruleExecutionId.add(governanceRuleService.enqueueAdhoc(accountId, governanceJobEnqueueDTO));
      }
    }

    return ResponseDTO.newResponse(GovernanceEnqueueResponseDTO.builder().ruleExecutionId(ruleExecutionId).build());
  }

  @NextGenManagerAuth
  @GET
  @Path("ruleSchema")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get Schema for entity", nickname = "ruleSchema")
  @Operation(operationId = "ruleSchema", description = "Get Schema for entity", summary = "Get Schema for entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "ruleSchema", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  getEntityYamlSchemaCustodian(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier) {
    return ResponseDTO.newResponse(governanceRuleService.getSchema());
  }

  @NextGenManagerAuth
  @POST
  @Path("ruleValidate")
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  @ApiOperation(value = "Validate a rule", nickname = "ValidateRule")
  @LogAccountIdentifier
  @Operation(operationId = "ValidateRule", description = "Validate a Rule .", summary = "Validate a rule",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "newly created rule", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public void
  validateRule(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(
          required = true, description = "Request body containing Rule uuid") @Valid CreateRuleDTO generateRule) {
    if (generateRule == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    Rule validateRule = generateRule.getRule();
    governanceRuleService.custodianValidate(validateRule);
  }
}