/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult;
import io.harness.delegate.beans.serverless.ServerlessRollbackResult;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaRollbackStepTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final SpecParameters specParameters =
      ServerlessAwsLambdaRollbackStepParameters.infoBuilder().serverlessAwsLambdaRollbackFnq("sadf").build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(specParameters).build();

  private final InfrastructureOutcome infrastructureOutcome =
      ServerlessAwsLambdaInfrastructureOutcome.builder().build();
  private final ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder().build();
  private final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();

  private final String previousVersionTimeStamp = "1234";
  private final boolean isFirstDeployment = false;
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final ServerlessAwsLambdaRollbackDataOutcome serverlessAwsLambdaRollbackDataOutcome =
      ServerlessAwsLambdaRollbackDataOutcome.builder()
          .previousVersionTimeStamp(previousVersionTimeStamp)
          .isFirstDeployment(isFirstDeployment)
          .build();
  private final String manifestFileOverrideContent = "asfdasfd";
  private final Pair<String, String> manifestFilePathContent = Pair.of("a", "b");
  private final ServerlessGitFetchOutcome serverlessGitFetchOutcome =
      ServerlessGitFetchOutcome.builder()
          .manifestFilePathContent(manifestFilePathContent)
          .manifestFileOverrideContent(manifestFileOverrideContent)
          .build();
  private final OptionalSweepingOutput serverlessRollbackDataOptionalOutput =
      OptionalSweepingOutput.builder().output(serverlessAwsLambdaRollbackDataOutcome).found(true).build();
  private final OptionalSweepingOutput serverlessGitFetchOptionalOutput =
      OptionalSweepingOutput.builder().output(serverlessGitFetchOutcome).found(true).build();

  public static final String SERVERLESS_AWS_LAMBDA_ROLLBACK_COMMAND_NAME = "ServerlessAwsLambdaRollback";

  private final ServerlessManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder().build();

  private Map<String, Object> manifestParams = new HashMap<>();

  private final String stage = "stage";
  private final String rollbackTimeStamp = "rollbackTimeStamp";
  private final String service = "service";
  private final String region = "region";
  private ServerlessRollbackResult serverlessRollbackResult = ServerlessAwsLambdaRollbackResult.builder()
                                                                  .stage(stage)
                                                                  .rollbackTimeStamp(rollbackTimeStamp)
                                                                  .service(service)
                                                                  .region(region)
                                                                  .build();
  private List<UnitProgress> unitProgressList = Arrays.asList();
  private final ServerlessCommandResponse serverlessCommandResponse =
      ServerlessRollbackResponse.builder()
          .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgressList).build())
          .serverlessRollbackResult(serverlessRollbackResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

  private final ServerInstanceInfo serverInstanceInfo = ServerlessAwsLambdaServerInstanceInfo.builder().build();
  private final List<ServerInstanceInfo> serverInstanceInfoList = Arrays.asList(serverInstanceInfo);

  @InjectMocks private ServerlessAwsLambdaRollbackStep serverlessAwsLambdaRollbackStep;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private OutcomeService outcomeService;
  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Mock private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private AccountService accountService;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void validateResourcesTest() {
    // no code written
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacIfNoRollbackFunctionSpecifiedTest() {
    SpecParameters specParametersWithNoRollbackFn = ServerlessAwsLambdaRollbackStepParameters.infoBuilder().build();
    StepElementParameters stepElementParametersWithNoRollbackFnSpec =
        StepElementParameters.builder().spec(specParametersWithNoRollbackFn).build();
    SkipTaskRequest skipTaskRequest =
        SkipTaskRequest.newBuilder()
            .setMessage("Serverless Aws Lambda Deploy step was not executed. Skipping rollback.")
            .build();
    TaskRequest taskRequest = serverlessAwsLambdaRollbackStep.obtainTaskAfterRbac(
        ambiance, stepElementParametersWithNoRollbackFnSpec, stepInputPackage);
    assertThat(taskRequest.getSkipTaskRequest()).isEqualTo(skipTaskRequest);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacIfRollbackFunctionSpecifiedTest() {
    doReturn(serverlessRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                ((ServerlessAwsLambdaRollbackStepParameters) specParameters).getServerlessAwsLambdaRollbackFnq() + "."
                + OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_DATA_OUTCOME));
    doReturn(serverlessGitFetchOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                ((ServerlessAwsLambdaRollbackStepParameters) specParameters).getServerlessAwsLambdaRollbackFnq() + "."
                + OutcomeExpressionConstants.SERVERLESS_GIT_FETCH_OUTCOME));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn(manifestsOutcome).when(serverlessStepCommonHelper).resolveServerlessManifestsOutcome(ambiance);
    doReturn(manifestOutcome)
        .when(serverlessStepCommonHelper)
        .getServerlessManifestOutcome(manifestsOutcome.values(), serverlessAwsLambdaStepHelper);

    manifestParams.put("manifestFileOverrideContent", serverlessGitFetchOutcome.getManifestFileOverrideContent());
    manifestParams.put("manifestFilePathContent", serverlessGitFetchOutcome.getManifestFilePathContent());

    doReturn(serverlessManifestConfig)
        .when(serverlessStepCommonHelper)
        .getServerlessManifestConfig(manifestParams, manifestOutcome, ambiance, serverlessAwsLambdaStepHelper);

    ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder()
            .previousVersionTimeStamp(serverlessAwsLambdaRollbackDataOutcome.getPreviousVersionTimeStamp())
            .isFirstDeployment(serverlessAwsLambdaRollbackDataOutcome.isFirstDeployment())
            .build();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessRollbackRequest serverlessRollbackRequest =
        ServerlessRollbackRequest.builder()
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_ROLLBACK)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .serverlessManifestConfig(serverlessManifestConfig)
            .serverlessRollbackConfig(serverlessAwsLambdaRollbackConfig)
            .commandName(SERVERLESS_AWS_LAMBDA_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .manifestContent(serverlessGitFetchOutcome.getManifestFileOverrideContent())
            .build();

    TaskRequest expectedTaskRequest = TaskRequest.newBuilder().build();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder().taskRequest(expectedTaskRequest).build();

    doReturn(taskChainResponse)
        .when(serverlessStepCommonHelper)
        .queueServerlessTask(stepElementParameters, serverlessRollbackRequest, ambiance,
            ServerlessExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build());
    TaskRequest taskRequest =
        serverlessAwsLambdaRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isEqualTo(expectedTaskRequest);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @SneakyThrows
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() {
    AccountDTO accountDTO = AccountDTO.builder().name("sfd").build();

    ServerlessAwsLambdaRollbackOutcome serverlessAwsLambdaRollbackOutcome = ServerlessAwsLambdaRollbackOutcome.builder()
                                                                                .stage(stage)
                                                                                .rollbackTimeStamp(rollbackTimeStamp)
                                                                                .region(region)
                                                                                .service(service)
                                                                                .build();

    doReturn(accountDTO).when(accountService).getAccount(AmbianceUtils.getAccountId(ambiance));
    StepResponse stepResponse = serverlessAwsLambdaRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> serverlessCommandResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().get().getOutcome())
        .isEqualTo(serverlessAwsLambdaRollbackOutcome);
  }
}
