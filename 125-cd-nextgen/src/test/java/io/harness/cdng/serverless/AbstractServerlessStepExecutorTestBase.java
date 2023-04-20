/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.beans.ServerlessAwsLambdaStepExecutorParams;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExecutorParams;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactoryArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeployConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public abstract class AbstractServerlessStepExecutorTestBase extends CategoryTest {
  @Mock protected ServerlessStepCommonHelper serverlessStepHelper;
  @Mock protected ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;

  @Mock protected InfrastructureOutcome infrastructureOutcome;
  @Mock protected StoreConfig storeConfig;

  protected ServerlessAwsLambdaManifestOutcome manifestOutcome;
  protected final String accountId = "accountId";
  protected final String releaseName = "releaseName";
  protected final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
  protected final ServerlessAwsLambdaManifestConfig manifestDelegateConfig =
      ServerlessAwsLambdaManifestConfig.builder().build();
  protected final UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList()).build();
  protected final ServerlessDeployConfig serverlessDeployConfig = ServerlessAwsLambdaDeployConfig.builder().build();
  protected final ServerlessManifestConfig serverlessManifestConfig =
      ServerlessAwsLambdaManifestConfig.builder().build();
  protected final String manifestFileOverrideContent = "adsf";
  protected final ServerlessInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder().build();
  protected final ServerlessStepExecutorParams serverlessStepExecutorParams =
      ServerlessAwsLambdaStepExecutorParams.builder()
          .manifestFilePathContent(Pair.of("a", "b"))
          .manifestFileOverrideContent(manifestFileOverrideContent)
          .build();
  protected final String SERVERLESS_AWS_LAMBDA_DEPLOY_COMMAND_NAME = "ServerlessAwsLambdaDeploy";

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);

    manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder().store(storeConfig).build();

    doReturn(serverlessInfraConfig)
        .when(serverlessStepHelper)
        .getServerlessInfraConfig(infrastructureOutcome, ambiance);
    doReturn(TaskChainResponse.builder().chainEnd(true).build())
        .when(serverlessStepHelper)
        .startChainLink(any(), any(), any());
    doReturn(Optional.empty()).when(serverlessStepHelper).resolveArtifactsOutcome(ambiance);
  }

  protected <T extends ServerlessDeployRequest> T executeTask(
      StepElementParameters stepElementParameters, Class<T> requestType) {
    ServerlessExecutionPassThroughData passThroughData =
        ServerlessExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder().build();
    ArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().build();
    doReturn(Optional.of(artifactOutcome)).when(serverlessStepHelper).resolveArtifactsOutcome(eq(ambiance));
    doReturn(serverlessArtifactConfig).when(serverlessStepHelper).getArtifactConfig(eq(artifactOutcome), eq(ambiance));
    doReturn(serverlessDeployConfig)
        .when(serverlessStepHelper)
        .getServerlessDeployConfig(
            (ServerlessAwsLambdaDeployStepParameters) stepElementParameters.getSpec(), serverlessAwsLambdaStepHelper);

    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put("manifestFileOverrideContent", manifestFileOverrideContent);
    manifestParams.put("manifestFilePathContent",
        ((ServerlessAwsLambdaStepExecutorParams) serverlessStepExecutorParams).getManifestFilePathContent());

    doReturn(serverlessManifestConfig)
        .when(serverlessStepHelper)
        .getServerlessManifestConfig(manifestParams, manifestOutcome, ambiance, serverlessAwsLambdaStepHelper);

    getServerlessAwsLambdaStepExecutor().executeServerlessTask(manifestOutcome, ambiance, stepElementParameters,
        passThroughData, unitProgressData, serverlessStepExecutorParams);
    ArgumentCaptor<T> requestCaptor = ArgumentCaptor.forClass(requestType);
    verify(serverlessStepHelper, times(1))
        .queueServerlessTask(
            eq(stepElementParameters), requestCaptor.capture(), eq(ambiance), eq(passThroughData), eq(true));
    return requestCaptor.getValue();
  }

  protected abstract ServerlessStepExecutor getServerlessAwsLambdaStepExecutor();
}
