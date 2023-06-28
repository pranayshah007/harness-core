/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.service.impl.NGTriggerYamlSchemaServiceImpl;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.schema.JacksonClassHelper;
import io.harness.yaml.schema.SwaggerGenerator;
import io.harness.yaml.schema.YamlSchemaException;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaHelper;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.validator.EnumCodeSchemaHandler;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.RequiredCodeSchemaHandler;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidatorTypeCode;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public class NGTriggerYamlSchemaServiceTest extends CategoryTest {
  ClassLoader classLoader;
  @InjectMocks NGTriggerYamlSchemaServiceImpl ngTriggerYamlSchemaServiceImpl;
  NGTriggerYamlSchemaService ngTriggerYamlSchemaService;
  @Mock YamlSchemaProvider yamlSchemaProvider;
  @Mock YamlSchemaValidator yamlSchemaValidator;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  String projectId = "projectId";
  String orgIdentifier = "orgIdentifier";
  String identifier = "identifier";

  @Before
  public void setup() throws Exception {
    classLoader = getClass().getClassLoader();
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    JacksonClassHelper jacksonClassHelper = new JacksonClassHelper(objectMapper);
    SwaggerGenerator swaggerGenerator = new SwaggerGenerator(objectMapper);
    List<YamlSchemaRootClass> rootClasses = List.of(YamlSchemaRootClass.builder()
                                                        .entityType(EntityType.TRIGGERS)
                                                        .availableAtProjectLevel(true)
                                                        .availableAtOrgLevel(false)
                                                        .availableAtAccountLevel(false)
                                                        .clazz(NGTriggerConfigV2.class)
                                                        .build());
    YamlSchemaGenerator yamlSchemaGenerator =
        new YamlSchemaGenerator(jacksonClassHelper, swaggerGenerator, rootClasses);
    Map<EntityType, JsonNode> schemas = yamlSchemaGenerator.generateYamlSchema();
    YamlSchemaValidator realYamlSchemaValidator =
        new YamlSchemaValidator(rootClasses, new EnumCodeSchemaHandler(), new RequiredCodeSchemaHandler());
    YamlSchemaHelper yamlSchemaHelper = new YamlSchemaHelper(rootClasses);
    realYamlSchemaValidator.initializeValidatorWithSchema(schemas);
    yamlSchemaHelper.initializeSchemaMaps(schemas);
    YamlSchemaProvider realYamlSchemaProvider = new YamlSchemaProvider(yamlSchemaHelper);
    ngTriggerYamlSchemaService =
        new NGTriggerYamlSchemaServiceImpl(realYamlSchemaProvider, realYamlSchemaValidator, objectMapper);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testValidateTriggerYaml() throws IOException {
    String filename = "ng-custom-trigger-v0.yaml";
    ObjectNode triggerSchema = mock(ObjectNode.class);
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    Set validationMessages = new HashSet<>();
    validationMessages.add(ValidationMessage.of("type", ValidatorTypeCode.ENUM, yaml, "[Http]"));
    when(yamlSchemaProvider.getYamlSchema(any(), any(), any(), any())).thenReturn(triggerSchema);
    when(yamlSchemaValidator.validateWithDetailedMessage(anyString(), any(JsonSchema.class)))
        .thenReturn(validationMessages);
    YamlSchemaErrorWrapperDTO errorWrapperDTO = YamlSchemaErrorWrapperDTO.builder().build();
    when(yamlSchemaValidator.processAndHandleValidationMessage(
             YamlPipelineUtils.getMapper().readTree(yaml), validationMessages, yaml))
        .thenThrow(new InvalidYamlException("error", errorWrapperDTO, yaml));
    assertThatThrownBy(() -> ngTriggerYamlSchemaServiceImpl.validateTriggerYaml(yaml, projectId, orgIdentifier, ""))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("error");

    doThrow(new InvalidRequestException("message"))
        .when(yamlSchemaValidator)
        .processAndHandleValidationMessage(YamlPipelineUtils.getMapper().readTree(yaml), validationMessages, yaml);
    assertThatThrownBy(() -> ngTriggerYamlSchemaServiceImpl.validateTriggerYaml(yaml, projectId, orgIdentifier, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error while validating trigger yaml");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlSchema() throws IOException {
    String generatedSchema =
        ngTriggerYamlSchemaService.getTriggerYamlSchema(projectId, orgIdentifier, identifier, Scope.PROJECT)
            .toPrettyString();
    String storedSchema = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("triggerYamlSchema.json")), StandardCharsets.UTF_8);
    if (!generatedSchema.equals(storedSchema)) {
      log.info("Difference in trigger's yaml schema :\n" + StringUtils.difference(generatedSchema, storedSchema));
      throw new YamlSchemaException(
          "Yaml schema not updated for NG Triggers. Please update the triggerYamlSchema.json accordingly.");
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateTriggerYamlWithInvalidTriggerIdentifier() throws IOException {
    String yaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-v0-invalid-identifier.yaml")),
            StandardCharsets.UTF_8);
    assertThatThrownBy(() -> ngTriggerYamlSchemaService.validateTriggerYaml(yaml, projectId, orgIdentifier, identifier))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("$.identifier: does not match the regex pattern ^[a-zA-Z_][0-9a-zA-Z_]{0,127}$");
  }
}
