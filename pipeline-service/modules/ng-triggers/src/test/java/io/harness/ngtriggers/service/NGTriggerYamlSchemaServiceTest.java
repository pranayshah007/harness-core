/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.ngtriggers.service.impl.NGTriggerYamlSchemaServiceImpl;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidatorTypeCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NGTriggerYamlSchemaServiceTest extends CategoryTest {
  @InjectMocks NGTriggerYamlSchemaServiceImpl ngTriggerYamlSchemaServiceImpl;
  @Mock YamlSchemaProvider yamlSchemaProvider;
  @Mock YamlSchemaValidator yamlSchemaValidator;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  String projectId = "projectId";
  String orgIdentifier = "orgIdentifier";
  String identifier = "identifier";
  @Before
  public void setup() throws Exception {}

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testValidateTriggerYaml() throws IOException {
    String filename = "ng-custom-trigger-v0.yaml";
    ClassLoader classLoader = getClass().getClassLoader();
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
}
