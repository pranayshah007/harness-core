/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class ServiceSpecStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks ServiceSpecStep serviceSpecStep;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetFinalVariablesMap() {
    Ambiance ambiance = Ambiance.newBuilder().setExpressionFunctorToken(12345).build();
    ServiceSpecStepParameters stepParameters =
        ServiceSpecStepParameters.builder()
            .originalVariables(ParameterField.createValueField(
                Arrays.asList(prepareVariable("v1", "10"), prepareVariable("v2", "20"), prepareVariable("v3", "30"))))
            .stageOverrideVariables(
                ParameterField.createValueField(Collections.singletonList(prepareVariable("v1", "12"))))
            .build();
    Map<String, Object> finalVariables = serviceSpecStep.getFinalVariablesMap(ambiance, stepParameters, null, null);
    assertThat(finalVariables).isNotNull();
    assertThat(((ParameterField<?>) finalVariables.get("v1")).getValue()).isEqualTo("10");
    assertThat(((ParameterField<?>) finalVariables.get("v2")).getValue()).isEqualTo("20");
    assertThat(((ParameterField<?>) finalVariables.get("v3")).getValue()).isEqualTo("30");

    Map<String, Object> output = (Map<String, Object>) finalVariables.get("output");
    assertThat(((ParameterField<?>) output.get("v1")).getValue()).isEqualTo("12");
    assertThat(((ParameterField<?>) output.get("v2")).getValue()).isEqualTo("20");
    assertThat(((ParameterField<?>) output.get("v3")).getValue()).isEqualTo("30");
  }

  private NGVariable prepareVariable(String name, String value) {
    return StringNGVariable.builder().name(name).value(ParameterField.createValueField(value)).build();
  }
}
