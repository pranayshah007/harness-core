/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.variables.NGServiceOverrides;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentMapperTest extends CDNGTestBase {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void toOutcome() {
    Map<String, Object> serviceOverrides = new HashMap<>();
    serviceOverrides.put("service", NGServiceOverrides.builder().build());

    Map<String, Object> variables = new HashMap<>();
    variables.put("variable", NGServiceOverrides.builder().build());

    EnvironmentStepParameters environmentStepParameters = EnvironmentStepParameters.builder()
                                                              .name("name")
                                                              .environmentRef(ParameterField.createValueField("ref"))
                                                              .identifier("identifier")
                                                              .description("desc")
                                                              .serviceOverrides(serviceOverrides)
                                                              .variables(variables)
                                                              .build();
    EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(environmentStepParameters);
    assertThat(environmentOutcome.getEnvironmentRef()).isEqualTo("ref");
    assertThat(environmentOutcome.getName()).isEqualTo("name");
    assertThat(environmentOutcome.getDescription()).isEqualTo("desc");
    assertThat(environmentOutcome.getIdentifier()).isEqualTo("identifier");
    assertThat(environmentOutcome.getVariables().containsKey("variable")).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testToEnvironmentStepParameters() {
    EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
        EnvironmentPlanCreatorConfig.builder()
            .environmentRef(ParameterField.createValueField("ref"))
            .name("name")
            .identifier("identifier")
            .description("description")
            .serviceOverrides(
                NGServiceOverrides.builder()
                    .serviceRef("ser")
                    .variables(Collections.singletonList(StringNGVariable.builder()
                                                             .name("name")
                                                             .value(ParameterField.createValueField("value"))
                                                             .build()))
                    .build())
            .build();
    EnvironmentStepParameters environmentStepParameters =
        EnvironmentMapper.toEnvironmentStepParameters(environmentPlanCreatorConfig);
    assertThat(environmentStepParameters.getEnvironmentRef().getValue()).isEqualTo("ref");
    assertThat(environmentStepParameters.getIdentifier()).isEqualTo("identifier");
    assertThat(environmentStepParameters.getName()).isEqualTo("name");
    assertThat(environmentStepParameters.getDescription()).isEqualTo("description");
    assertThat(environmentStepParameters.getServiceOverrides().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testOverrideOutcome() {
    Map<String, Object> serviceOverrides = new HashMap<>();
    serviceOverrides.put("val1", "value1");
    serviceOverrides.put("val2", "value2");

    Map<String, Object> variables = new HashMap<>();
    variables.put("val3", "value3");
    variables.put("val4", "value4");

    variables.put("val1", "newValue1");

    EnvironmentStepParameters environmentStepParameters = EnvironmentStepParameters.builder()
                                                              .environmentRef(ParameterField.createValueField("ref"))
                                                              .variables(variables)
                                                              .serviceOverrides(serviceOverrides)
                                                              .build();
    EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(environmentStepParameters);

    Map<String, Object> resultedVariables = environmentOutcome.getVariables();
    assertThat(resultedVariables.size()).isEqualTo(4);
    assertThat(resultedVariables.get("val1")).isEqualTo("value1");
    assertThat(resultedVariables.get("val2")).isEqualTo("value2");
    assertThat(resultedVariables.get("val3")).isEqualTo("value3");
    assertThat(resultedVariables.get("val4")).isEqualTo("value4");
  }
}