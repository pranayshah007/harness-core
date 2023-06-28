/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.beans.FeatureName.PIE_STATIC_YAML_SCHEMA;
import static io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper.APPROVAL_NAMESPACE;
import static io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper.FLATTENED_PARALLEL_STEP_ELEMENT_CONFIG_SCHEMA;
import static io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper.PARALLEL_STEP_ELEMENT_CONFIG;
import static io.harness.yaml.schema.beans.SchemaConstants.ALL_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ONE_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.SPEC_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGES_NODE;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.JsonSchemaException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.manage.ManagedExecutorService;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.YamlSchemaTransientHelper;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.utils.YamlSchemaUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PMSYamlSchemaServiceImpl implements PMSYamlSchemaService {
  private final Executor executor = new ManagedExecutorService(Executors.newFixedThreadPool(4));

  private static final String STEP_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StepElementConfig.class);
  public static final String STAGE_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StageElementConfig.class);

  public static final long SCHEMA_TIMEOUT = 10;

  private final YamlSchemaProvider yamlSchemaProvider;
  private final YamlSchemaValidator yamlSchemaValidator;
  private final PmsSdkInstanceService pmsSdkInstanceService;
  private final PmsYamlSchemaHelper pmsYamlSchemaHelper;
  private final SchemaFetcher schemaFetcher;
  private final PmsFeatureFlagService pmsFeatureFlagService;

  private ExecutorService yamlSchemaExecutor;

  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  Integer allowedParallelStages;

  private final String PIPELINE_JSON = "pipeline.json";
  private final String TEMPLATE_JSON = "template.json";

  @Inject
  public PMSYamlSchemaServiceImpl(YamlSchemaProvider yamlSchemaProvider, YamlSchemaValidator yamlSchemaValidator,
      PmsSdkInstanceService pmsSdkInstanceService, PmsYamlSchemaHelper pmsYamlSchemaHelper, SchemaFetcher schemaFetcher,
      @Named("allowedParallelStages") Integer allowedParallelStages,
      @Named("YamlSchemaExecutorService") ExecutorService executor, PmsFeatureFlagService pmsFeatureFlagService) {
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.pmsSdkInstanceService = pmsSdkInstanceService;
    this.pmsYamlSchemaHelper = pmsYamlSchemaHelper;
    this.schemaFetcher = schemaFetcher;
    this.allowedParallelStages = allowedParallelStages;
    this.yamlSchemaExecutor = executor;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
  }

  @Override
  public JsonNode getPipelineYamlSchema(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    try {
      return getPipelineYamlSchemaInternal(accountIdentifier, projectIdentifier, orgIdentifier, scope);
    } catch (Exception e) {
      log.error("[PMS] Failed to get pipeline yaml schema");
      throw new JsonSchemaException(e.getMessage());
    }
  }

  @Override
  public boolean validateYamlSchema(String accountId, String orgId, String projectId, String yaml) {
    // Keeping pipeline yaml schema validation behind ff. If ff is disabled then schema validation will happen. Will
    // remove after finding the root cause of invalid schema generation and fixing it.
    if (!pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, accountId)) {
      Future<Boolean> future =
          yamlSchemaExecutor.submit(() -> validateYamlSchemaInternal(accountId, orgId, projectId, yaml));
      try (AutoLogContext accountLogContext =
               new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
        return future.get(SCHEMA_TIMEOUT, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // If e.getCause() instance of InvalidYamlException then it means we got some legit schema-validation errors and
        // it has error info according to the schema-error-experience.
        if (e.getCause() != null && e.getCause() instanceof io.harness.yaml.validator.InvalidYamlException) {
          throw(io.harness.yaml.validator.InvalidYamlException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      } catch (TimeoutException | InterruptedException e) {
        log.error(format("Timeout while validating schema for accountId: %s, orgId: %s, projectId: %s", accountId,
                      orgId, projectId),
            e);
        // if validation does not happen before timeout, we will skip the validation and allow the operations(Pipeline
        // save/execute).
        return true;
      }
    }
    return true;
  }

  @Override
  public boolean validateYamlSchema(String accountId, String orgId, String projectId, JsonNode jsonNode) {
    // Keeping pipeline yaml schema validation behind ff. If ff is disabled then schema validation will happen. Will
    // remove after finding the root cause of invalid schema generation and fixing it.
    if (!pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, accountId)) {
      Future<Boolean> future =
          yamlSchemaExecutor.submit(() -> validateYamlSchemaInternal(accountId, orgId, projectId, jsonNode));
      try (AutoLogContext accountLogContext =
               new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
        return future.get(SCHEMA_TIMEOUT, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // If e.getCause() instance of InvalidYamlException then it means we got some legit schema-validation errors and
        // it has error info according to the schema-error-experience.
        if (e.getCause() != null && e.getCause() instanceof io.harness.yaml.validator.InvalidYamlException) {
          throw(io.harness.yaml.validator.InvalidYamlException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      } catch (TimeoutException | InterruptedException e) {
        log.error(format("Timeout while validating schema for accountId: %s, orgId: %s, projectId: %s", accountId,
                      orgId, projectId),
            e);
        // if validation does not happen before timeout, we will skip the validation and allow the operations(Pipeline
        // save/execute).
        return true;
      }
    }
    return true;
  }

  @VisibleForTesting
  boolean validateYamlSchemaInternal(String accountIdentifier, String orgId, String projectId, String yaml) {
    long start = System.currentTimeMillis();
    try {
      JsonNode schema = null;

      // If static schema ff is on, fetch schema from fetcher
      if (pmsFeatureFlagService.isEnabled(accountIdentifier, PIE_STATIC_YAML_SCHEMA)) {
        schema = schemaFetcher.fetchStaticYamlSchema(accountIdentifier);
      } else {
        schema = getPipelineYamlSchema(accountIdentifier, projectId, orgId, Scope.PROJECT);
      }

      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      yamlSchemaValidator.validate(yaml, schemaString,
          pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier),
          allowedParallelStages, PIPELINE_NODE + "/" + STAGES_NODE);
      return true;
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[PMS_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      if (ex instanceof NullPointerException
          || ex.getCause() != null && ex.getCause() instanceof NullPointerException) {
        log.error(format(
            "Schema validation thrown NullPointerException. Please check the generated schema for account: %s, org: %s, project: %s",
            accountIdentifier, orgId, projectId));
        return false;
      }
      log.error(ex.getMessage(), ex);
      throw new JsonSchemaValidationException(ex.getMessage(), ex);
    }
  }

  // TODO(shalini): remove older methods with yaml string once all are moved to jsonNode
  @VisibleForTesting
  boolean validateYamlSchemaInternal(String accountIdentifier, String orgId, String projectId, JsonNode jsonNode) {
    long start = System.currentTimeMillis();
    try {
      JsonNode schema = getPipelineYamlSchema(accountIdentifier, projectId, orgId, Scope.PROJECT);
      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      yamlSchemaValidator.validate(jsonNode, schemaString,
          pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier),
          allowedParallelStages, PIPELINE_NODE + "/" + STAGES_NODE);
      return true;
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[PMS_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      if (ex instanceof NullPointerException
          || ex.getCause() != null && ex.getCause() instanceof NullPointerException) {
        log.error(format(
            "Schema validation thrown NullPointerException. Please check the generated schema for account: %s, org: %s, project: %s",
            accountIdentifier, orgId, projectId));
        return false;
      }
      log.error(ex.getMessage(), ex);
      throw new JsonSchemaValidationException(ex.getMessage(), ex);
    }
  }

  @Override
  public void validateUniqueFqn(String yaml) {
    try {
      FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml).getNode().getCurrJsonNode());
    } catch (IOException ex) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public void invalidateAllCache() {
    schemaFetcher.invalidateAllCache();
  }

  private JsonNode getPipelineYamlSchemaInternal(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    JsonNode pipelineSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.PIPELINES, orgIdentifier, projectIdentifier, scope);
    JsonNode pipelineSteps =
        yamlSchemaProvider.getYamlSchema(EntityType.PIPELINE_STEPS, orgIdentifier, projectIdentifier, scope);

    ObjectNode pipelineDefinitions = (ObjectNode) pipelineSchema.get(DEFINITIONS_NODE);
    ObjectNode pipelineStepsDefinitions = (ObjectNode) pipelineSteps.get(DEFINITIONS_NODE);

    JsonNodeUtils.deletePropertiesInJsonNode(
        (ObjectNode) pipelineSchema.get(DEFINITIONS_NODE).get("PipelineInfoConfig"), "required");

    ObjectNode mergedDefinitions = (ObjectNode) JsonNodeUtils.merge(pipelineDefinitions, pipelineStepsDefinitions);

    // Merging the schema for all steps that are moved to new schema.
    ObjectNode finalMergedDefinitions = yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier,
        scope, mergedDefinitions, pmsYamlSchemaHelper.getNodeEntityTypesByYamlGroup(StepCategory.STEP.name()));
    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, mergedDefinitions,
        pmsYamlSchemaHelper.getNodeEntityTypesByYamlGroup(StepCategory.STAGE.name()));
    YamlSchemaTransientHelper.removeV2StepEnumsFromStepElementConfig(finalMergedDefinitions.get(STEP_ELEMENT_CONFIG));
    ObjectNode stageElementConfig = (ObjectNode) pipelineDefinitions.get(STAGE_ELEMENT_CONFIG);
    YamlSchemaTransientHelper.deleteSpecNodeInStageElementConfig(stageElementConfig);

    PmsYamlSchemaHelper.flattenParallelElementConfig(pipelineDefinitions);

    List<ModuleType> enabledModules = obtainEnabledModules(accountIdentifier);
    enabledModules.add(ModuleType.PMS);
    List<YamlSchemaWithDetails> schemaWithDetailsList =
        fetchSchemaWithDetailsFromModules(accountIdentifier, enabledModules);
    List<YamlSchemaWithDetails> stepsSchemaWithDetails =
        schemaWithDetailsList.stream()
            .filter(o -> o.getYamlSchemaMetadata().getYamlGroup().getGroup().equals(StepCategory.STEP.name()))
            .collect(Collectors.toList());
    CompletableFutures<List<PartialSchemaDTO>> completableFutures = new CompletableFutures<>(executor);
    for (ModuleType enabledModule : enabledModules) {
      List<YamlSchemaWithDetails> moduleYamlSchemaDetails =
          filterYamlSchemaDetailsByModule(stepsSchemaWithDetails, enabledModule);
      completableFutures.supplyAsync(
          () -> schemaFetcher.fetchSchema(accountIdentifier, enabledModule, moduleYamlSchemaDetails));
    }

    try {
      List<List<PartialSchemaDTO>> partialSchemaDTOList = completableFutures.allOf().get(2, TimeUnit.MINUTES);

      partialSchemaDTOList.stream()
          .filter(Objects::nonNull)
          .forEach(partialSchemaDTOList1
              -> partialSchemaDTOList1.forEach(partialSchemaDTO
                  -> pmsYamlSchemaHelper.processPartialStageSchema(finalMergedDefinitions, pipelineStepsDefinitions,
                      stageElementConfig, partialSchemaDTO, accountIdentifier)));
      // These logs are only for debugging the invalid schema generation issue. Checking only for approval stage
      if (!finalMergedDefinitions.get(APPROVAL_NAMESPACE)
               .get(PARALLEL_STEP_ELEMENT_CONFIG)
               .toString()
               .equals(FLATTENED_PARALLEL_STEP_ELEMENT_CONFIG_SCHEMA)) {
        log.warn(
            "Final flattened ParallelStepElementConfig schema is incorrect for approval stage merging all stage schemas for account after {}",
            accountIdentifier);
      }
    } catch (Exception e) {
      log.error(format("[PMS] Exception while merging yaml schema: %s", e.getMessage()), e);
    }

    log.info("[PMS] Merging all stages into pipeline schema");
    pmsYamlSchemaHelper.processStageSchema(schemaWithDetailsList, pipelineDefinitions);
    // Remove duplicate if then statements from stage element config. Keep references only to new ones we added above.
    removeDuplicateIfThenFromStageElementConfig(stageElementConfig);

    return ((ObjectNode) pipelineSchema).set(DEFINITIONS_NODE, pipelineDefinitions);
  }

  @VisibleForTesting
  void removeDuplicateIfThenFromStageElementConfig(ObjectNode stageElementConfig) {
    ArrayNode stageElementConfigAllOfNode =
        getAllOfNodeWithTypeAndSpec((ArrayNode) stageElementConfig.get(ONE_OF_NODE));
    if (stageElementConfigAllOfNode == null) {
      return;
    }

    Iterator<JsonNode> elements = stageElementConfigAllOfNode.elements();
    while (elements.hasNext()) {
      JsonNode element = elements.next();
      JsonNode refNode = element.findValue(REF_NODE);
      if (refNode != null && refNode.isValueNode()) {
        if (refNode.asText().equals("#/definitions/ApprovalStageConfig")
            || refNode.asText().equals("#/definitions/FeatureFlagStageConfig")) {
          elements.remove();
        }
      }
    }
  }

  private ArrayNode getAllOfNodeWithTypeAndSpec(ArrayNode node) {
    for (int i = 0; i < node.size(); i++) {
      if (node.get(i).get(PROPERTIES_NODE).get(SPEC_NODE) != null) {
        return (ArrayNode) node.get(i).get(ALL_OF_NODE);
      }
    }
    return null;
  }

  private List<YamlSchemaWithDetails> filterYamlSchemaDetailsByModule(
      List<YamlSchemaWithDetails> allYamlSchemaWithDetails, ModuleType moduleType) {
    List<YamlSchemaWithDetails> moduleYamlSchemaDetails = new ArrayList<>();
    for (YamlSchemaWithDetails yamlSchemaWithDetails : allYamlSchemaWithDetails) {
      if (yamlSchemaWithDetails.getYamlSchemaMetadata() != null
          && yamlSchemaWithDetails.getYamlSchemaMetadata().getModulesSupported() != null
          && yamlSchemaWithDetails.getYamlSchemaMetadata().getModulesSupported().contains(moduleType)) {
        moduleYamlSchemaDetails.add(yamlSchemaWithDetails);
      }
    }
    return moduleYamlSchemaDetails;
  }

  @SuppressWarnings("unchecked")
  private List<ModuleType> obtainEnabledModules(String accountIdentifier) {
    List<ModuleType> modules =
        ModuleType.getModules().stream().filter(moduleType -> !moduleType.isInternal()).collect(Collectors.toList());

    // TODO: Ideally it should be received from accountLicenses but there were some issues observed this part.
    //    AccountLicenseDTO accountLicense =
    //        NGRestUtils.getResponse(ngLicenseHttpClient.getAccountLicensesDTO(accountIdentifier));
    //    accountLicense.getAllModuleLicenses().forEach((moduleType, value) -> {
    //      if (EmptyPredicate.isNotEmpty(value)) {
    //        modules.add(moduleType);
    //      }
    //    });

    List<ModuleType> instanceModuleTypes = pmsSdkInstanceService.getActiveInstanceNames()
                                               .stream()
                                               .map(ModuleType::fromString)
                                               .collect(Collectors.toList());

    if (instanceModuleTypes.size() != modules.size()) {
      log.debug(
          "There is a mismatch of instanceModuleTypes and projectModuleTypes. Please investigate if the sdk is registered or not");
    }
    return (List<ModuleType>) CollectionUtils.intersection(modules, instanceModuleTypes);
  }

  protected List<YamlSchemaWithDetails> fetchSchemaWithDetailsFromModules(
      String accountIdentifier, List<ModuleType> moduleTypes) {
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    for (ModuleType moduleType : moduleTypes) {
      try {
        yamlSchemaWithDetailsList.addAll(
            schemaFetcher.fetchSchemaDetail(accountIdentifier, moduleType).getYamlSchemaWithDetailsList());
      } catch (Exception e) {
        log.error(e.getMessage());
      }
    }
    return yamlSchemaWithDetailsList;
  }

  // Introduce cache here.
  @Override
  public JsonNode getIndividualYamlSchema(String accountId, String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup) {
    if (StepCategory.PIPELINE.toString().equals(yamlGroup)) {
      return getPipelineYamlSchemaInternal(accountId, projectIdentifier, orgIdentifier, null);
    }
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = null;
    Map<String, List<JsonNode>> nameSpaceToDefinitionMap = new HashMap<>();
    Set<String> nameSpaces = new HashSet<>();
    JsonNode mergedDefinition = null;
    Map<String, JsonNode> finalNameSpaceToDefinitionMap = new HashMap<>();
    if (StepCategory.STAGE.toString().equals(yamlGroup) || StepCategory.STEP_GROUP.toString().equals(yamlGroup)) {
      List<ModuleType> enabledModules = obtainEnabledModules(accountId);
      enabledModules.add(ModuleType.PMS);
      yamlSchemaWithDetailsList = fetchSchemaWithDetailsFromModules(accountId, enabledModules);
      nameSpaces =
          yamlSchemaWithDetailsList.stream()
              .filter(o -> o.getYamlSchemaMetadata().getYamlGroup().getGroup().equals(StepCategory.STAGE.name()))
              .map(o -> o.getYamlSchemaMetadata().getNamespace())
              .collect(Collectors.toSet());
      yamlSchemaWithDetailsList =
          yamlSchemaWithDetailsList.stream()
              .filter(o -> o.getYamlSchemaMetadata().getYamlGroup().getGroup().equals(StepCategory.STEP.name()))
              .collect(Collectors.toList());
      yamlSchemaWithDetailsList =
          filterYamlSchemaDetailsByModule(yamlSchemaWithDetailsList, entityType.getEntityProduct());
      // Hack to handle proper schema generation for stage
      for (YamlSchemaWithDetails yamlSchemaWithDetails : yamlSchemaWithDetailsList) {
        String nameSpace = yamlSchemaWithDetails.getYamlSchemaMetadata().getNamespace();
        JsonNode definition = yamlSchemaWithDetails.getSchema().get(DEFINITIONS_NODE);
        nameSpaces.add(nameSpace);
        // Creating a map of all definitions corresponding to all namespace
        if (EmptyPredicate.isEmpty(nameSpace)) {
          if (mergedDefinition == null) {
            mergedDefinition = definition;
          } else {
            JsonNodeUtils.merge(mergedDefinition, definition);
          }
        } else {
          if (nameSpaceToDefinitionMap.containsKey(nameSpace)) {
            nameSpaceToDefinitionMap.get(nameSpace).add(definition);
          } else {
            List<JsonNode> nameSpaceDefinition = new LinkedList<>();
            nameSpaceDefinition.add(definition);
            nameSpaceToDefinitionMap.put(nameSpace, nameSpaceDefinition);
          }
        }
      }
      // Multiple schemas might have same namespace. Mergint them into one
      for (Map.Entry<String, List<JsonNode>> entry : nameSpaceToDefinitionMap.entrySet()) {
        JsonNode nameSpaceDefinition = null;
        for (JsonNode jsonNode : entry.getValue()) {
          if (nameSpaceDefinition == null) {
            nameSpaceDefinition = jsonNode;
          } else {
            JsonNodeUtils.merge(nameSpaceDefinition, jsonNode);
          }
        }
        finalNameSpaceToDefinitionMap.put(entry.getKey(), nameSpaceDefinition);
      }
    }
    JsonNode jsonNode = schemaFetcher.fetchStepYamlSchema(accountId, projectIdentifier, orgIdentifier, scope,
        entityType, getYamlGroup(yamlGroup), yamlSchemaWithDetailsList);

    String stepNameSpace = null;
    if (StepCategory.STAGE.toString().equals(yamlGroup) || StepCategory.STEP_GROUP.toString().equals(yamlGroup)) {
      if (jsonNode.get(DEFINITIONS_NODE).fields().hasNext()) {
        String nameSpace = jsonNode.get(DEFINITIONS_NODE).fields().next().getKey();
        if (nameSpaces.contains(nameSpace)) {
          stepNameSpace = nameSpace;
        }
      }

      if (mergedDefinition != null) {
        // Merging definitions fetched from different modules with stage schema
        JsonNodeUtils.merge(jsonNode.get(DEFINITIONS_NODE), mergedDefinition);
      }
      for (Map.Entry<String, JsonNode> entry : finalNameSpaceToDefinitionMap.entrySet()) {
        if (!stepNameSpace.equals(entry.getKey())) {
          // Adding definitions to individual namespace and the root definition
          if (jsonNode.get(DEFINITIONS_NODE).get(entry.getKey()) == null) {
            ((ObjectNode) jsonNode.get(DEFINITIONS_NODE)).putIfAbsent(entry.getKey(), entry.getValue());
          } else {
            JsonNodeUtils.merge(jsonNode.get(DEFINITIONS_NODE).get(entry.getKey()), entry.getValue());
          }
          JsonNodeUtils.merge(jsonNode.get(DEFINITIONS_NODE), entry.getValue());
        }
      }

      // TODO: hack to remove v2 steps from stage yamls. Fix it properly
      for (String nameSpace : nameSpaces) {
        if (jsonNode.get(DEFINITIONS_NODE).get(nameSpace) != null) {
          YamlSchemaTransientHelper.removeV2StepEnumsFromStepElementConfig(
              jsonNode.get(DEFINITIONS_NODE).get(nameSpace).get(STEP_ELEMENT_CONFIG));
        }
      }
    } else {
      JsonNode stepSpecTypeNode = getStepSpecType();
      JsonNodeUtils.merge(jsonNode.get(DEFINITIONS_NODE), stepSpecTypeNode);
    }

    if (StepCategory.STEP_GROUP.toString().equals(yamlGroup)) {
      // create new jsonNode schema for stepGroup
      ObjectNode stepGroupSchema = new ObjectNode(JsonNodeFactory.instance);
      stepGroupSchema.putIfAbsent("type", jsonNode.get("type"));
      stepGroupSchema.putIfAbsent("$schema", jsonNode.get("$schema"));
      stepGroupSchema.putIfAbsent("definitions", jsonNode.get("definitions"));
      stepGroupSchema.putIfAbsent("$ref", getStepGroupProperties(stepNameSpace));
      return stepGroupSchema;
    }
    return jsonNode;
  }

  @Override
  public JsonNode getStaticSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier,
      String identifier, EntityType entityType, Scope scope, String version) {
    // Appending branch and json in url
    String fileUrl = calculateFileURL(entityType, version);

    try {
      // Read the JSON file as JsonNode
      log.info(format("Fetching static schema with file URL %s ", fileUrl));
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(new URL(fileUrl));

      return jsonNode;
    } catch (Exception ex) {
      log.error(format("Not able to read file from %s path", fileUrl));
    }
    return null;
  }

  /*
  Based on environment and entityType, URL is created. For qa/stress branch is quality-assurance, for all other
  supported env branch will be master
   */
  public String calculateFileURL(EntityType entityType, String version) {
    String fileURL = pipelineServiceConfiguration.getStaticSchemaFileURL();

    String entityTypeJson = "";
    switch (entityType) {
      case PIPELINES:
        entityTypeJson = PIPELINE_JSON;
        break;
      case TEMPLATE:
        entityTypeJson = TEMPLATE_JSON;
        break;
      default:
        entityTypeJson = PIPELINE_JSON;
        log.error("Code should never reach here {}", entityType);
    }

    return format(fileURL, version, entityTypeJson);
  }

  private String getYamlGroup(String yamlGroup) {
    return StepCategory.STEP_GROUP.toString().equals(yamlGroup) ? StepCategory.STAGE.toString() : yamlGroup;
  }

  // TODO: Brijesh to look at the intermittent issue and remove this
  private JsonNode getStepSpecType() {
    String stepSpecTypeNodeString = "{\"StepSpecType\": {\n"
        + "                    \"type\": \"object\",\n"
        + "                    \"discriminator\": \"type\",\n"
        + "                    \"$schema\": \"http://json-schema.org/draft-07/schema#\"\n"
        + "                }}";
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readTree(stepSpecTypeNodeString);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonNode getStepGroupProperties(String namespace) {
    if (EmptyPredicate.isNotEmpty(namespace)) {
      namespace = namespace + "/";
    }
    String stepGroupProperties = "\"#/definitions/" + namespace + "StepGroupElementConfig\"";
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readTree(stepGroupProperties);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
