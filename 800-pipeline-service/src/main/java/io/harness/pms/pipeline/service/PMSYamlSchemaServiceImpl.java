/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.yaml.schema.beans.SchemaConstants.ALL_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ONE_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.SPEC_NODE;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.JsonSchemaException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.jackson.JsonNodeUtils;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.manage.ManagedExecutorService;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.YamlSchemaTransientHelper;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.utils.YamlSchemaUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PMSYamlSchemaServiceImpl implements PMSYamlSchemaService {
  private final Executor executor = new ManagedExecutorService(Executors.newFixedThreadPool(4));

  private static final String STEP_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StepElementConfig.class);
  public static final String STAGE_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StageElementConfig.class);
  public static final Class<StageElementConfig> STAGE_ELEMENT_CONFIG_CLASS = StageElementConfig.class;

  private final YamlSchemaProvider yamlSchemaProvider;
  private final YamlSchemaGenerator yamlSchemaGenerator;
  private final YamlSchemaValidator yamlSchemaValidator;
  private final PmsSdkInstanceService pmsSdkInstanceService;
  private final PmsYamlSchemaHelper pmsYamlSchemaHelper;
  private final ApprovalYamlSchemaService approvalYamlSchemaService;
  private final FeatureFlagYamlService featureFlagYamlService;
  private final SchemaFetcher schemaFetcher;
  private final NgLicenseHttpClient ngLicenseHttpClient;
  Integer allowedParallelStages;

  @Inject
  public PMSYamlSchemaServiceImpl(YamlSchemaProvider yamlSchemaProvider, YamlSchemaGenerator yamlSchemaGenerator,
      YamlSchemaValidator yamlSchemaValidator, PmsSdkInstanceService pmsSdkInstanceService,
      PmsYamlSchemaHelper pmsYamlSchemaHelper, ApprovalYamlSchemaService approvalYamlSchemaService,
      FeatureFlagYamlService featureFlagYamlService, SchemaFetcher schemaFetcher,
      NgLicenseHttpClient ngLicenseHttpClient, @Named("allowedParallelStages") Integer allowedParallelStages) {
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaGenerator = yamlSchemaGenerator;
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.pmsSdkInstanceService = pmsSdkInstanceService;
    this.pmsYamlSchemaHelper = pmsYamlSchemaHelper;
    this.approvalYamlSchemaService = approvalYamlSchemaService;
    this.featureFlagYamlService = featureFlagYamlService;
    this.schemaFetcher = schemaFetcher;
    this.ngLicenseHttpClient = ngLicenseHttpClient;
    this.allowedParallelStages = allowedParallelStages;
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
  public void validateYamlSchema(String accountId, String orgId, String projectId, String yaml) {
    validateYamlSchemaInternal(accountId, orgId, projectId, yaml);
  }

  private void validateYamlSchemaInternal(String accountIdentifier, String orgId, String projectId, String yaml) {
    long start = System.currentTimeMillis();
    try {
      JsonNode schema = getPipelineYamlSchema(accountIdentifier, projectId, orgId, Scope.PROJECT);
      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      yamlSchemaValidator.validate(yaml, schemaString,
          pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier),
          allowedParallelStages);
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[PMS_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      log.error(ex.getMessage(), ex);
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(ex.getMessage(), ex, errorWrapperDTO);
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
                  -> pmsYamlSchemaHelper.processPartialStageSchema(
                      finalMergedDefinitions, pipelineStepsDefinitions, stageElementConfig, partialSchemaDTO)));
    } catch (Exception e) {
      log.error(format("[PMS] Exception while merging yaml schema: %s", e.getMessage()), e);
    }

    log.info("[PMS] Merging all stages into pipeline schema");
    pmsYamlSchemaHelper.processStageSchema(schemaWithDetailsList, pipelineDefinitions);
    // Remove duplicate if then statements from stage element config. Keep references only to new ones we added above.
    removeDuplicateIfThenFromStageElementConfig(stageElementConfig);

    return ((ObjectNode) pipelineSchema).set(DEFINITIONS_NODE, pipelineDefinitions);
  }

  private void removeDuplicateIfThenFromStageElementConfig(ObjectNode stageElementConfig) {
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
    List<ModuleType> modules = new ArrayList<>();
    AccountLicenseDTO accountLicense =
        NGRestUtils.getResponse(ngLicenseHttpClient.getAccountLicensesDTO(accountIdentifier));
    accountLicense.getAllModuleLicenses().forEach((moduleType, value) -> {
      if (EmptyPredicate.isNotEmpty(value)) {
        modules.add(moduleType);
      }
    });

    List<ModuleType> instanceModuleTypes = pmsSdkInstanceService.getActiveInstanceNames()
                                               .stream()
                                               .map(ModuleType::fromString)
                                               .collect(Collectors.toList());

    if (instanceModuleTypes.size() != modules.size()) {
      log.warn(
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
    if (yamlGroup.equals(StepCategory.PIPELINE.toString())) {
      return getPipelineYamlSchemaInternal(accountId, projectIdentifier, orgIdentifier, null);
    }
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = null;
    if (yamlGroup.equals(StepCategory.STAGE.toString())) {
      List<ModuleType> enabledModules = obtainEnabledModules(accountIdentifier);
      yamlSchemaWithDetailsList = fetchSchemaWithDetailsFromModules(accountId, enabledModules);
    }
    return schemaFetcher.fetchStepYamlSchema(
        accountId, projectIdentifier, orgIdentifier, scope, entityType, yamlGroup, yamlSchemaWithDetailsList);
  }
}
