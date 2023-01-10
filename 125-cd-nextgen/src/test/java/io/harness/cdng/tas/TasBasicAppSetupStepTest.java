/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasBasicAppSetupStepTest extends CDNGTestBase {
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks private TasBasicAppSetupStep tasBasicAppSetupStep;

  private final TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
      TanzuApplicationServiceInfrastructureOutcome.builder()
          .connectorRef("account.tas")
          .organization("dev-org")
          .space("dev-space")
          .build();
  private TasBasicAppSetupStepParameters parameters =
      TasBasicAppSetupStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final Ambiance ambiance = getAmbiance();
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_TAS_NG));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    tasBasicAppSetupStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_TAS_NG));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    assertThatThrownBy(() -> tasBasicAppSetupStep.validateResources(ambiance, stepElementParameters))
        .hasMessage("CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(tasBasicAppSetupStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
