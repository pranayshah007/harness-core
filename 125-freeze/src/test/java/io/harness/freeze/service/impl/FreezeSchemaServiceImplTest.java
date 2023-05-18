/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.rule.Owner;
import io.harness.yaml.schema.YamlSchemaException;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class FreezeSchemaServiceImplTest {
  @Mock YamlSchemaProvider yamlSchemaProvider;
  @Mock YamlSchemaValidator yamlSchemaValidator;

  @Spy @InjectMocks FreezeSchemaServiceImpl freezeSchemaService;

  private String yaml;
  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private JsonNode readJsonFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readTree(
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    String filename = "projectFreezeConfig.yaml";
    yaml = readFile(filename);
    on(freezeSchemaService).set("yamlSchemaProvider", yamlSchemaProvider);
    on(freezeSchemaService).set("yamlSchemaValidator", yamlSchemaValidator);
    when(yamlSchemaProvider.getYamlSchema(any(), any(), any(), any())).thenReturn(readJsonFile("freeze-schema.json"));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getFreezeSchema() {
    assertThat(freezeSchemaService.getFreezeSchema(ACCOUNT_ID, PROJ_IDENTIFIER, ORG_IDENTIFIER, Scope.PROJECT))
        .isEqualTo(readJsonFile("freeze-schema.json"));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getFreezeSchemaWithException() {
    when(yamlSchemaProvider.getYamlSchema(any(), any(), any(), any())).thenThrow(new YamlSchemaException("Exception"));
    assertThatThrownBy(() -> {
      freezeSchemaService.getFreezeSchema(ACCOUNT_ID, PROJ_IDENTIFIER, ORG_IDENTIFIER, Scope.PROJECT);
    }).isInstanceOf(JsonSchemaException.class);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void validateYamlSchema() throws IOException {
    freezeSchemaService.validateYamlSchema(
        NGFreezeDtoMapper.toFreezeConfigEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, FreezeType.MANUAL));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateFreezeSchemaWithYaml() throws IOException {
    when(yamlSchemaValidator.processAndHandleValidationMessage(any(), any(), any())).thenReturn(Collections.emptySet());
    when(yamlSchemaValidator.validateWithDetailedMessage(yaml, EntityType.FREEZE)).thenReturn(Collections.emptySet());
    freezeSchemaService.validateYamlSchema(yaml);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testInvalidateFreezeSchemaWithYaml() throws IOException {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder().schemaErrors(Collections.emptyList()).build();
    when(yamlSchemaValidator.processAndHandleValidationMessage(any(), any(), any()))
        .thenThrow(new InvalidYamlException("Exception", errorWrapperDTO, yaml));
    when(yamlSchemaValidator.validateWithDetailedMessage(yaml, EntityType.FREEZE)).thenReturn(Collections.emptySet());
    assertThatThrownBy(() -> {
      freezeSchemaService.validateYamlSchema(yaml);
    }).isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testValidateFreezeSchemaForFailure() {
    when(yamlSchemaValidator.processAndHandleValidationMessage(any(), any(), any()))
        .thenThrow(new NullPointerException());
    assertThatThrownBy(() -> freezeSchemaService.validateYamlSchema(yaml)).isInstanceOf(NullPointerException.class);
  }
}
