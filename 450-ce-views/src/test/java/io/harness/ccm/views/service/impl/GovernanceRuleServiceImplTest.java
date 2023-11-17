/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.SUJEESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.entities.Rule;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.google.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class GovernanceRuleServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private GovernanceRuleServiceImpl governanceRuleService;
  @Mock private YamlSchemaValidator yamlSchemaValidator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    final Field schemaField = ReflectionUtils.getFieldByName(GovernanceRuleServiceImpl.class, "ruleSchema");
    String schema = getDummySchema();
    ReflectionUtils.setObjectField(schemaField, governanceRuleService, schema);
  }

  @Test
  @Owner(developers = SUJEESH)
  @Category(UnitTests.class)
  public void testGetSchema() {
    String schema = governanceRuleService.getSchema();
    assertThat(schema).isNotBlank();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SUJEESH)
  @Category(UnitTests.class)
  public void testCustodianValidateError() throws IOException {
    String sampleYAML = "invalid yaml";
    Rule rule = Rule.builder().rulesYaml(sampleYAML).build();
    when(yamlSchemaValidator.validate(sampleYAML, governanceRuleService.getSchema()))
        .thenThrow(new InvalidYamlException("sample error", null, null, null));
    governanceRuleService.custodianValidate(rule);
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, governanceRuleService.getSchema());
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, EntityType.CCM_GOVERNANCE_RULE_AWS);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SUJEESH)
  @Category(UnitTests.class)
  public void testCustodianValidateIOException() throws IOException {
    String sampleYAML = "invalid yaml";
    Rule rule = Rule.builder().rulesYaml(sampleYAML).build();
    when(yamlSchemaValidator.validate(sampleYAML, governanceRuleService.getSchema())).thenThrow(new IOException());
    governanceRuleService.custodianValidate(rule);
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, governanceRuleService.getSchema());
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, EntityType.CCM_GOVERNANCE_RULE_AWS);
  }

  @Test()
  @Owner(developers = SUJEESH)
  @Category(UnitTests.class)
  public void testCustodianValidateSuccess() throws IOException {
    String sampleYAML = "invalid yaml";
    Rule rule = Rule.builder().rulesYaml(sampleYAML).build();
    when(yamlSchemaValidator.validate(sampleYAML, governanceRuleService.getSchema())).thenReturn(Set.of());
    governanceRuleService.custodianValidate(rule);
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, governanceRuleService.getSchema());
    verify(yamlSchemaValidator, times(1)).validate(sampleYAML, EntityType.CCM_GOVERNANCE_RULE_AWS);
  }

  private String getDummySchema() {
    return "{\"test_key\" : \"test_value\"}";
  }
}
