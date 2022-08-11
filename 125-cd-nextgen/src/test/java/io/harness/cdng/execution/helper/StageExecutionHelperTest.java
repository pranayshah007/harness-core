/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.StageStatus;

import com.google.common.collect.Lists;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class StageExecutionHelperTest extends CategoryTest {
  private static final String ENV_IDENTIFIER = "envIdentifier";
  private static final String INFRA_IDENTIFIER = "infraIdentifier";
  private static final String SERVICE_IDENTIFIER = "serviceIdentifier";
  private static final String INFRA_KIND = InfrastructureKind.PDC;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String EXECUTION_ID = "executionId";

  @Mock private CDStepHelper cdStepHelper;
  @Mock private StageExecutionInfoService stageExecutionInfoService;

  @InjectMocks private StageExecutionHelper stageExecutionHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfo() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ArtifactoryArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();

    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.ofNullable(artifactOutcome));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(scope)
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        INFRA_KIND);

    verify(stageExecutionInfoService)
        .save(StageExecutionInfo.builder()
                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                  .orgIdentifier(ORG_IDENTIFIER)
                  .projectIdentifier(PROJECT_IDENTIFIER)
                  .envIdentifier(ENV_IDENTIFIER)
                  .infraIdentifier(INFRA_IDENTIFIER)
                  .serviceIdentifier(SERVICE_IDENTIFIER)
                  .stageStatus(StageStatus.IN_PROGRESS)
                  .stageExecutionId(EXECUTION_ID)
                  .executionDetails(SshWinRmStageExecutionDetails.builder()
                                        .artifactsOutcome(Lists.newArrayList(artifactOutcome))
                                        .configFilesOutcome(configFilesOutcome)
                                        .build())
                  .build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoWithInvalidInfraKind() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.KUBERNETES_GCP);

    ArgumentCaptor<StageExecutionInfo> stageExecutionInfoArgumentCaptor =
        ArgumentCaptor.forClass(StageExecutionInfo.class);
    verify(stageExecutionInfoService, times(1)).save(stageExecutionInfoArgumentCaptor.capture());

    StageExecutionInfo value = stageExecutionInfoArgumentCaptor.getValue();
    assertThat(value.getExecutionDetails()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoWithoutInfraKind() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(()
                           -> stageExecutionHelper.saveStageExecutionInfo(ambiance,
                               ExecutionInfoKey.builder()
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .infraIdentifier(INFRA_IDENTIFIER)
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .build(),
                               null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Unable to save stage execution info, infrastructure kind cannot be null or empty, infrastructureKind: null, executionInfoKey: ExecutionInfoKey(scope=null, envIdentifier=envIdentifier, infraIdentifier=infraIdentifier, serviceIdentifier=serviceIdentifier, deploymentIdentifier=null)");
  }
}
