/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.environment.steps.EnvironmentStepV2;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentStepV2Test extends CategoryTest {
  @Mock private AccessControlClient accessControlClient;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private EnvironmentService environmentService;
  @InjectMocks private EnvironmentStepV2 environmentStepV2;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(environmentStepV2.getStepParametersClass()).isEqualTo(EnvironmentStepParameters.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac() {
    EnvironmentStepParameters environmentStepParameters = EnvironmentStepParameters.builder()
                                                              .name("name")
                                                              .environmentRef(ParameterField.createValueField("ref"))
                                                              .identifier("identifier")
                                                              .description("desc")
                                                              .build();

    ArgumentCaptor<Ambiance> ambiance = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<String> outcomeConstant = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<EnvironmentOutcome> outcome = ArgumentCaptor.forClass(EnvironmentOutcome.class);
    ArgumentCaptor<String> category = ArgumentCaptor.forClass(String.class);
    doReturn(null)
        .when(executionSweepingOutputResolver)
        .consume(ambiance.capture(), outcomeConstant.capture(), outcome.capture(), category.capture());
    environmentStepV2.executeSyncAfterRbac(Ambiance.newBuilder().build(), environmentStepParameters, null, null);

    assertThat(outcomeConstant.getValue()).isEqualTo(OutputExpressionConstants.ENVIRONMENT);
    assertThat(category.getValue()).isEqualTo(StepCategory.STAGE.name());
    assertThat(outcome.getValue().getIdentifier()).isEqualTo("identifier");
    assertThat(outcome.getValue().getEnvironmentRef()).isEqualTo("ref");
  }
}