/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.customstage;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.steps.customstage.CustomStageConfig;
import io.harness.steps.customstage.CustomStageNode;
import io.harness.utils.FeatureRestrictionsGetter;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class CustomStageYamlSchemaServiceImpl implements CustomStageYamlSchemaService {
  private static final String CUSTOM_NAMESPACE = "custom";
  private static final String CUSTOM_STAGE_NODE = YamlSchemaUtils.getSwaggerName(CustomStageNode.class);

  @Inject private YamlSchemaProvider yamlSchemaProvider;
  @Inject private PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @Inject private YamlSchemaGenerator yamlSchemaGenerator;
  @Inject private List<YamlSchemaRootClass> yamlSchemaRootClasses;
  @Inject private FeatureRestrictionsGetter featureRestrictionsGetter;

  @Override
  public PartialSchemaDTO getCustomStageYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    JsonNode customStageSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.CUSTOM_STAGE, orgIdentifier, projectIdentifier, scope);

    JsonNode definitions = customStageSchema.get(DEFINITIONS_NODE);

    JsonNode jsonNode = definitions.get(StepElementConfig.class.getSimpleName());
    pmsYamlSchemaHelper.modifyStepElementSchema((ObjectNode) jsonNode);

    jsonNode = definitions.get(ParallelStepElementConfig.class.getSimpleName());
    if (jsonNode.isObject()) {
      PmsYamlSchemaHelper.flatten((ObjectNode) jsonNode);
    }

    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, (ObjectNode) definitions,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()));

    yamlSchemaGenerator.modifyRefsNamespace(customStageSchema, CUSTOM_NAMESPACE);

    Set<String> enabledFeatureFlags =
        pmsYamlSchemaHelper.getEnabledFeatureFlags(accountIdentifier, yamlSchemaWithDetailsList);
    Map<String, Boolean> featureRestrictionsMap =
        featureRestrictionsGetter.getFeatureRestrictionsAvailability(yamlSchemaWithDetailsList, accountIdentifier);

    // false is added to support cross service steps schema
    if (yamlSchemaWithDetailsList != null) {
      YamlSchemaUtils.addOneOfInExecutionWrapperConfig(customStageSchema.get(DEFINITIONS_NODE),
          yamlSchemaWithDetailsList, ModuleType.PMS, enabledFeatureFlags, featureRestrictionsMap, false);
    }

    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    JsonNode node = mapper.createObjectNode().set(CUSTOM_NAMESPACE, definitions);

    JsonNode partialCustomStageSchema = ((ObjectNode) customStageSchema).set(DEFINITIONS_NODE, node);

    return PartialSchemaDTO.builder()
        .namespace(CUSTOM_NAMESPACE)
        .nodeName(CUSTOM_STAGE_NODE)
        .schema(partialCustomStageSchema)
        .nodeType(getCustomStageTypeName())
        .moduleType(ModuleType.PMS)
        .skipStageSchema(false)
        .build();
  }

  private String getCustomStageTypeName() {
    JsonTypeName annotation = CustomStageConfig.class.getAnnotation(JsonTypeName.class);
    return annotation.value();
  }
}
