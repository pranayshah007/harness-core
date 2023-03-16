/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_RULE_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_RULE_DELETE;
import static io.harness.ccm.remote.resources.TelemetryConstants.GOVERNANCE_RULE_UPDATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.remote.resources.TelemetryConstants.RULE_NAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.governance.faktory.FaktoryProducer;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CloneRuleDTO;
import io.harness.ccm.views.dto.CreateRuleDTO;
import io.harness.ccm.views.dto.GovernanceEnqueueResponseDTO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.dto.ListDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleClone;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.GovernanceJobDetailsAWS;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionStatusType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.helper.RuleStoreType;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.GovernanceConfig;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

@NextGenManagerAuth

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

  // Internal API for OOTB rule creation
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
    if (!rule.getIsOOTB()) {
      rule.setAccountId(accountId);
    } else if (rule.getAccountId().equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      rule.setAccountId(GLOBAL_ACCOUNT_ID);
    } else {
      throw new InvalidRequestException("Not authorised to create OOTB rules. Make a custom rule instead");
    }
    if (governanceRuleService.fetchByName(accountId, rule.getName(), true) != null) {
      throw new InvalidRequestException("Rule with the given name already exits");
    }
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    RuleList ruleList = governanceRuleService.list(governancePolicyFilter);
    GovernanceConfig governanceConfig = configuration.getGovernanceConfig();
    if (ruleList.getRule().size() >= governanceConfig.getPolicyPerAccountLimit()) {
      throw new InvalidRequestException("You have exceeded the limit for rules creation");
    }
    // TO DO: Handle this for custom rules and git connectors
    rule.setStoreType(RuleStoreType.INLINE);
    rule.setVersionLabel("0.0.1");
    rule.setDeleted(false);
    governanceRuleService.validateAWSSchema(rule);
    governanceRuleService.custodianValidate(rule);
    governanceRuleService.save(rule);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_NAME, rule.getName());
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_CREATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(

        Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new RuleCreateEvent(accountId, rule.toDTO()));
          return governanceRuleService.fetchByName(accountId, rule.getName(), false);
        })));
  }

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
    rule.toDTO();
    Rule oldRule = governanceRuleService.fetchById(accountId, rule.getUuid(), true);
    if (oldRule.getIsOOTB()) {
      throw new InvalidRequestException("Editing OOTB rule is not allowed");
    }
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(RULE_NAME, oldRule.getName());
    if (rule.getRulesYaml() != null) {
      Rule testSchema = Rule.builder().build();
      testSchema.setName(oldRule.getName());
      testSchema.setRulesYaml(rule.getRulesYaml());
      governanceRuleService.validateAWSSchema(testSchema);
      governanceRuleService.custodianValidate(testSchema);
    }
    governanceRuleService.update(rule, accountId);
    Rule updatedRule = governanceRuleService.fetchById(accountId, rule.getUuid(), true);
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_UPDATED, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);

    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleUpdateEvent(accountId, updatedRule.toDTO(), oldRule.toDTO()));
      return updatedRule;
    })));
  }

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
    rule.toDTO();
    if (!rule.getAccountId().equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Editing OOTB rule is not allowed");
    }
    Rule oldRule = governanceRuleService.fetchById(accountId, rule.getUuid(), true);
    if (rule.getRulesYaml() != null) {
      Rule testSchema = Rule.builder().build();
      testSchema.setName(oldRule.getName());
      testSchema.setRulesYaml(rule.getRulesYaml());
      governanceRuleService.validateAWSSchema(testSchema);
      governanceRuleService.custodianValidate(testSchema);
    }
    return ResponseDTO.newResponse(governanceRuleService.update(rule, GLOBAL_ACCOUNT_ID));
  }
  // Internal API for deletion of OOTB rules

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
          required = true, description = "Unique identifier for the rule") @NotNull @Valid String uuid) {
    if (!accountId.equals(configuration.getGovernanceConfig().getOOTBAccount())) {
      throw new InvalidRequestException("Deleting OOTB rule is not allowed");
    }
    governanceRuleService.fetchById(GLOBAL_ACCOUNT_ID, uuid, false);
    boolean result = governanceRuleService.delete(GLOBAL_ACCOUNT_ID, uuid);
    return ResponseDTO.newResponse(result);
  }

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
    telemetryReporter.sendTrackEvent(GOVERNANCE_RULE_DELETE, null, accountId, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(Failsafe.with(transactionRetryRule).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new RuleDeleteEvent(accountId, rule.toDTO()));
      return governanceRuleService.delete(accountId, uuid);
    })));
  }

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

  @POST
  @Path("enqueue")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Enqueues job for execution", nickname = "enqueueGovernanceJob")
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
      RuleCloudProviderType ruleCloudProviderType = ruleEnforcement.getCloudProvider();
      accountId = ruleEnforcement.getAccountId();
      if (ruleEnforcement.getCloudProvider() != RuleCloudProviderType.AWS) {
        log.error("Support for non AWS cloud providers is not present atm. Skipping enqueuing in faktory");
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
      List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
      PageResponse<ConnectorResponseDTO> response = null;
      ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
          ConnectorFilterPropertiesDTO.builder()
              .types(Arrays.asList(ConnectorType.CE_AWS))
              .ccmConnectorFilter(CcmConnectorFilter.builder()
                                      .featuresEnabled(Arrays.asList(CEFeatures.GOVERNANCE))
                                      .awsAccountIds(ruleEnforcement.getTargetAccounts())
                                      .build())
              .build();
      connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
      int page = 0;
      int size = 100;
      do {
        response = NGRestUtils.getResponse(connectorResourceClient.listConnectors(
            accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
        if (response != null && isNotEmpty(response.getContent())) {
          nextGenConnectorResponses.addAll(response.getContent());
        }
        page++;
      } while (response != null && isNotEmpty(response.getContent()));

      log.info(
          "For rule enforcement setting {}: Got connector data: {}", ruleEnforcementUuid, nextGenConnectorResponses);

      // Step-4 Enqueue in faktory
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
        for (String region : ruleEnforcement.getTargetRegions()) {
          for (Rule rule : rulesList) {
            try {
              GovernanceJobDetailsAWS governanceJobDetailsAWS =
                  GovernanceJobDetailsAWS.builder()
                      .accountId(accountId)
                      .awsAccountId(ceAwsConnectorDTO.getAwsAccountId())
                      .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                      .roleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                      .isDryRun(ruleEnforcement.getIsDryRun())
                      .ruleId(rule.getUuid())
                      .region(region)
                      .ruleEnforcementId(ruleEnforcementUuid)
                      .policy(rule.getRulesYaml())
                      .build();
              Gson gson = new GsonBuilder().create();
              String json = gson.toJson(governanceJobDetailsAWS);
              log.info("For rule enforcement setting {}: Enqueuing job in Faktory {}", ruleEnforcementUuid, json);
              // Bulk enqueue in faktory can lead to difficulties in error handling and retry.
              // order: jobType, jobQueue, json
              String jid = FaktoryProducer.push(configuration.getGovernanceConfig().getAwsFaktoryJobType(),
                  configuration.getGovernanceConfig().getAwsFaktoryQueueName(), json);
              log.info("For rule enforcement setting {}: Pushed job in Faktory: {}", ruleEnforcementUuid, jid);
              // Make a record in Mongo
              // TO DO: We can bulk insert in mongo for all successfull faktory job pushes
              RuleExecution ruleExecution = RuleExecution.builder()
                                                .accountId(accountId)
                                                .jobId(jid)
                                                .cloudProvider(ruleCloudProviderType)
                                                .executionLogPath("") // Updated by worker when execution finishes
                                                .isDryRun(ruleEnforcement.getIsDryRun())
                                                .ruleEnforcementIdentifier(ruleEnforcementUuid)
                                                .ruleEnforcementName(ruleEnforcement.getName())
                                                .executionCompletedAt(null) // Updated by worker when execution finishes
                                                .ruleIdentifier(rule.getUuid())
                                                .targetAccount(ceAwsConnectorDTO.getAwsAccountId())
                                                .targetRegions(Arrays.asList(region))
                                                .executionLogBucketType("")
                                                .ruleName(rule.getName())
                                                .OOTB(rule.getIsOOTB())
                                                .executionStatus(RuleExecutionStatusType.ENQUEUED)
                                                .build();
              enqueuedRuleExecutionIds.add(ruleExecutionService.save(ruleExecution));
            } catch (Exception e) {
              log.warn(
                  "Exception enqueueing job for ruleEnforcementUuid: {} for targetAccount: {} for targetRegions: {}, {}",
                  ruleEnforcementUuid, ceAwsConnectorDTO.getAwsAccountId(), region, e);
            }
          }
        }
      }
    } else {
      // Call is from UI for adhoc evaluation. Directly enqueue in this case
      // TO DO: See if UI adhoc requests can be sent to higher priority queue. This should also change in worker.
      log.info("enqueuing for ad-hoc request");
      if (isEmpty(accountId)) {
        throw new InvalidRequestException("Missing accountId");
      }
      List<Rule> rulesList = governanceRuleService.list(accountId, Arrays.asList(governanceJobEnqueueDTO.getRuleId()));
      if (rulesList == null) {
        log.error("For rule id {}: no rules exists in mongo. Nothing to enqueue", governanceJobEnqueueDTO.getRuleId());
        return ResponseDTO.newResponse(GovernanceEnqueueResponseDTO.builder().ruleExecutionId(null).build());
      }
      try {
        GovernanceJobDetailsAWS governanceJobDetailsAWS =
            GovernanceJobDetailsAWS.builder()
                .accountId(accountId)
                .awsAccountId(governanceJobEnqueueDTO.getTargetAccountId())
                .externalId(governanceJobEnqueueDTO.getExternalId())
                .roleArn(governanceJobEnqueueDTO.getRoleArn())
                .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
                .ruleId(governanceJobEnqueueDTO.getRuleId())
                .region(governanceJobEnqueueDTO.getTargetRegion())
                .ruleEnforcementId("") // This is adhoc run
                .policy(governanceJobEnqueueDTO.getPolicy())
                .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
                .build();
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(governanceJobDetailsAWS);
        log.info("Enqueuing job in Faktory {}", json);
        // jobType, jobQueue, json
        String jid = FaktoryProducer.push(configuration.getGovernanceConfig().getAwsFaktoryJobType(),
            configuration.getGovernanceConfig().getAwsFaktoryQueueName(), json);
        log.info("Pushed job in Faktory: {}", jid);
        // Make a record in Mongo
        RuleExecution ruleExecution = RuleExecution.builder()
                                          .accountId(accountId)
                                          .jobId(jid)
                                          .cloudProvider(governanceJobEnqueueDTO.getRuleCloudProviderType())
                                          .executionLogPath("") // Updated by worker when execution finishes
                                          .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
                                          .ruleEnforcementIdentifier(ruleEnforcementUuid)
                                          .executionCompletedAt(null) // Updated by worker when execution finishes
                                          .ruleIdentifier(governanceJobEnqueueDTO.getRuleId())
                                          .targetAccount(governanceJobEnqueueDTO.getTargetAccountId())
                                          .targetRegions(Arrays.asList(governanceJobEnqueueDTO.getTargetRegion()))
                                          .executionLogBucketType("")
                                          .resourceCount(0)
                                          .ruleName(rulesList.get(0).getName())
                                          .OOTB(rulesList.get(0).getIsOOTB())
                                          .executionStatus(RuleExecutionStatusType.ENQUEUED)
                                          .build();
        enqueuedRuleExecutionIds.add(ruleExecutionService.save(ruleExecution));
      } catch (Exception e) {
        log.warn("Exception enqueueing job for ruleEnforcementUuid: {} for targetAccount: {} for targetRegions: {}, {}",
            ruleEnforcementUuid, governanceJobEnqueueDTO.getTargetAccountId(),
            governanceJobEnqueueDTO.getTargetRegion(), e);
      }
    }
    return ResponseDTO.newResponse(
        GovernanceEnqueueResponseDTO.builder().ruleExecutionId(enqueuedRuleExecutionIds).build());
  }

  @GET
  @Path("entitySchema")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get Schema for entity", nickname = "getSchemaForEntity")
  @Operation(operationId = "getSchemaForEntity", description = "Get Schema for entity",
      summary = "Get Schema for entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Schema", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<JsonNode>
  getEntityYamlSchema(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.ENTITY_TYPE) EntityType entityType, Scope scope) {
    return ResponseDTO.newResponse(
        yamlSchemaProvider.getYamlSchema(entityType, orgIdentifier, projectIdentifier, scope));
  }
}