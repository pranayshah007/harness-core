/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.core.services.api.CVNGYamlSchemaService;
import io.harness.encryption.Scope;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.yaml.schema.SchemaGeneratorUtils;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CVNGYamlSchemaServiceImpl implements CVNGYamlSchemaService {
  private static final String CVNG_NAMESPACE = "cvng";

  private final YamlSchemaProvider yamlSchemaProvider;
  private final YamlSchemaGenerator yamlSchemaGenerator;
  private final Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes;
  private final List<YamlSchemaRootClass> yamlSchemaRootClasses;
  @Inject
  public CVNGYamlSchemaServiceImpl(YamlSchemaProvider yamlSchemaProvider, YamlSchemaGenerator yamlSchemaGenerator,
      @Named("yaml-schema-subtypes") Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes,
      List<YamlSchemaRootClass> yamlSchemaRootClasses) {
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaGenerator = yamlSchemaGenerator;
    this.yamlSchemaSubtypes = yamlSchemaSubtypes;
    this.yamlSchemaRootClasses = yamlSchemaRootClasses;
  }
  @Override
  public PartialSchemaDTO getMergedDeploymentStageYamlSchema(
      String projectIdentifier, String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> stepSchemaWithDetails) {
    // Will return null once CV steps are moved to new schema because CV does not have its own stage.
    return getDeploymentStageYamlSchemaUtil(projectIdentifier, orgIdentifier, scope, stepSchemaWithDetails);
  }

  @Override
  public List<YamlSchemaWithDetails> getDeploymentStageYamlSchemaWithDetails(
      String projectIdentifier, String orgIdentifier, Scope scope) {
    return yamlSchemaProvider.getCrossFunctionalStepsSchemaDetails(projectIdentifier, orgIdentifier, scope,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()), ModuleType.CV);
  }

  @Override
  public JsonNode getStepYamlSchema(EntityType entityType, String orgId, String projectId, Scope scope) {
    return yamlSchemaProvider.getYamlSchema(entityType, orgId, projectId, scope);
  }

  @Override
  public List<PartialSchemaDTO> getDeploymentStageYamlSchema(
      String projectIdentifier, String orgIdentifier, Scope scope) {
    return Collections.singletonList(getDeploymentStageYamlSchemaUtil(projectIdentifier, orgIdentifier, scope, null));
  }

  // stepSchemaWithDetails would be empty because CV is not a stage. No cross-functional step should ask to make it
  // available to CV module.
  public PartialSchemaDTO getDeploymentStageYamlSchemaUtil(
      String orgIdentifier, String projectIdentifier, Scope scope, List<YamlSchemaWithDetails> stepSchemaWithDetails) {
    JsonNode deploymentSteps =
        yamlSchemaProvider.getYamlSchema(EntityType.VERIFY_STEP, orgIdentifier, projectIdentifier, scope);
    JsonNode deploymentImpactSteps = yamlSchemaProvider.getYamlSchema(
        EntityType.ANALYZE_DEPLOYMENT_IMPACT_STEP, orgIdentifier, projectIdentifier, scope);
    JsonNode definitions = deploymentSteps.get(DEFINITIONS_NODE);
    JsonNodeUtils.merge(definitions, deploymentImpactSteps);
    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, (ObjectNode) definitions,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()));

    yamlSchemaGenerator.modifyRefsNamespace(deploymentSteps, CVNG_NAMESPACE);
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    JsonNode node = mapper.createObjectNode().set(CVNG_NAMESPACE, definitions);
    JsonNode partialCVNGSchema = ((ObjectNode) deploymentSteps).set(DEFINITIONS_NODE, node);
    return PartialSchemaDTO.builder()
        .namespace(CVNG_NAMESPACE)
        .nodeName("CVNGStageInfo")
        .schema(partialCVNGSchema)
        .nodeType(getVerifyStepTypeName())
        .moduleType(ModuleType.CV)
        .skipStageSchema(true)
        .build();
  }

  private String getVerifyStepTypeName() {
    JsonTypeName annotation = CVNGStepInfo.class.getAnnotation(JsonTypeName.class);
    return annotation.value();
  }
}
