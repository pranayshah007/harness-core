/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.yaml.schema.beans.SchemaConstants.CONST_NODE;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerYamlSchemaServiceImpl implements NGTriggerYamlSchemaService {
  private final YamlSchemaProvider yamlSchemaProvider;
  private final YamlSchemaValidator yamlSchemaValidator;

  @Override
  public JsonNode getTriggerYamlSchema(String projectIdentifier, String orgIdentifier, String identifier, Scope scope) {
    JsonNode schema = yamlSchemaProvider.getYamlSchema(EntityType.TRIGGERS, orgIdentifier, projectIdentifier, scope);

    if (schema != null && isNotEmpty(identifier)) {
      schema = yamlSchemaProvider.upsertInObjectFieldAtSecondLevelInSchema(
          schema, NGCommonEntityConstants.IDENTIFIER_KEY, CONST_NODE, identifier);
    }

    return schema;
  }

  @Override
  public void validateTriggerYaml(String yaml, String projectIdentifier, String orgIdentifier, String identifier) {
    try {
      JsonNode jsonNode = getTriggerYamlSchema(projectIdentifier, orgIdentifier, identifier, Scope.PROJECT);
      JsonSchemaFactory factory =
          JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)).build();
      JsonSchema schema = factory.getSchema(jsonNode);
      Set<ValidationMessage> validationMessages = yamlSchemaValidator.validateWithDetailedMessage(yaml, schema);
      yamlSchemaValidator.processAndHandleValidationMessage(
          YamlPipelineUtils.getMapper().readTree(yaml), validationMessages, yaml);
    } catch (InvalidYamlException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException("Error while validating trigger yaml");
    }
  }
}
