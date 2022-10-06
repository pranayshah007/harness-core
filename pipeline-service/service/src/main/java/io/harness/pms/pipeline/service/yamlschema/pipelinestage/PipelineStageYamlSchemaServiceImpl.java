/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.pipelinestage;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
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

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineStageYamlSchemaServiceImpl implements PipelineStageYamlSchemaService {
  private static final String PIPELINE_NAMESPACE = "pipeline";
  private static final String PIPELINE_STAGE_NODE = YamlSchemaUtils.getSwaggerName(PipelineStageNode.class);

  @Inject private YamlSchemaProvider yamlSchemaProvider;
  @Inject private YamlSchemaGenerator yamlSchemaGenerator;
  @Inject private List<YamlSchemaRootClass> yamlSchemaRootClasses;

  @Override
  public PartialSchemaDTO getPipelineStageYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    JsonNode pipelineStageSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.PIPELINE_STAGE, orgIdentifier, projectIdentifier, scope);

    JsonNode definitions = pipelineStageSchema.get(DEFINITIONS_NODE);

    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, (ObjectNode) definitions,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()));

    yamlSchemaGenerator.modifyRefsNamespace(pipelineStageSchema, PIPELINE_NAMESPACE);

    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    JsonNode node = mapper.createObjectNode().set(PIPELINE_NAMESPACE, definitions);

    JsonNode partialCustomStageSchema = ((ObjectNode) pipelineStageSchema).set(DEFINITIONS_NODE, node);

    return PartialSchemaDTO.builder()
        .namespace(PIPELINE_NAMESPACE)
        .nodeName(PIPELINE_STAGE_NODE)
        .schema(partialCustomStageSchema)
        .nodeType(getPipelineStageTypeName())
        .moduleType(ModuleType.PMS)
        .skipStageSchema(false)
        .build();
  }

  private String getPipelineStageTypeName() {
    JsonTypeName annotation = PipelineStageConfig.class.getAnnotation(JsonTypeName.class);
    return annotation.value();
  }
}
