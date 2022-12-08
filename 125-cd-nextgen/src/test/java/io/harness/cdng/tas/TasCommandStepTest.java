/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

public class TasCommandStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final TasCommandStepParameters tasCommandStepParameters = TasCommandStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(tasCommandStepParameters).timeout(ParameterField.createValueField("10m")).build();

  @Mock private CDStepHelper cdStepHelper;


  @Spy @InjectMocks private TasCommandStep tasCommandStep;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTasTaskTest() {
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    String key = "key";
    String value = "value";
    Map<String,String> allFilesFetch = Map.of(key, value);
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
    List<String> pathsFromScript = Arrays.asList();
    String rawScript = "rawScript";
    String repoRoot = "repoRoot";
    TasExecutionPassThroughData tasExecutionPassThroughData =
            TasExecutionPassThroughData.builder()
                    .allFilesFetched(allFilesFetch)
                    .cfCliVersion(CfCliVersionNG.V7)
                    .pathsFromScript(pathsFromScript)
                    .rawScript(rawScript)
                    .repoRoot(repoRoot)
                    .build();
    ManifestOutcome manifestOutcome = TasManifestOutcome.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(TaskRequest.newBuilder().build());

    TaskChainResponse taskChainResponse = tasCommandStep.executeTasTask(manifestOutcome, ambiance, stepElementParameters, tasExecutionPassThroughData, true,
            unitProgressData);

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(TasExecutionPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(tasExecutionPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }
}
