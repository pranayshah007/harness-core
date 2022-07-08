/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessDeployResult;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeployConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.LogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessAwsLambdaStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Mock private LogCallback mockLogCallback;

  private static final String SOME_URL = "https://url.com/owner/repo.git";

  @Spy @InjectMocks private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;

  @Before
  public void setup() {}

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessManifestOutcomeTest() {
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder().build();
    List<ManifestOutcome> manifestOutcomeList = Arrays.asList(manifestOutcome);

    assertThat(serverlessAwsLambdaStepHelper.getServerlessManifestOutcome(manifestOutcomeList))
        .isEqualTo(manifestOutcome);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessManifestOutcomeWhenNoManifestsTest() {
    List<ManifestOutcome> manifestOutcomeList = Arrays.asList();
    serverlessAwsLambdaStepHelper.getServerlessManifestOutcome(manifestOutcomeList);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessManifestOutcomeWhenMoreThanOneManifestTest() {
    ManifestOutcome manifestOutcome1 = ServerlessAwsLambdaManifestOutcome.builder().build();
    ManifestOutcome manifestOutcome2 = ServerlessAwsLambdaManifestOutcome.builder().build();
    List<ManifestOutcome> manifestOutcomeList = Arrays.asList(manifestOutcome1, manifestOutcome2);
    serverlessAwsLambdaStepHelper.getServerlessManifestOutcome(manifestOutcomeList);
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getConfigOverridePathWhenManifestNotOfServerlessAwsLambdaTypeTest() {
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();
    serverlessAwsLambdaStepHelper.getConfigOverridePath(manifestOutcome);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getConfigOverridePathSuccessTest() {
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder()
                                          .configOverridePath(ParameterField.createValueField("adsf"))
                                          .build();
    assertThat(serverlessAwsLambdaStepHelper.getConfigOverridePath(manifestOutcome)).isEqualTo("adsf");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessDeployConfigSuccessTest() {
    ServerlessSpecParameters serverlessSpecParameters = ServerlessAwsLambdaDeployStepParameters.infoBuilder()
                                                            .commandOptions(ParameterField.createValueField("adsf"))
                                                            .build();
    ServerlessDeployConfig serverlessDeployConfig =
        serverlessAwsLambdaStepHelper.getServerlessDeployConfig(serverlessSpecParameters);
    assertThat(serverlessDeployConfig).isInstanceOf(ServerlessAwsLambdaDeployConfig.class);
    assertThat(((ServerlessAwsLambdaDeployConfig) serverlessDeployConfig).getCommandOptions()).isEqualTo("adsf");
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessDeployConfigFailureTest() {
    ServerlessSpecParameters serverlessSpecParameters = ServerlessAwsLambdaRollbackStepParameters.infoBuilder().build();
    serverlessAwsLambdaStepHelper.getServerlessDeployConfig(serverlessSpecParameters);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessManifestConfigSuccessTest() {
    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put("manifestFilePathContent", Pair.of("a", "b"));
    manifestParams.put("manifestFileOverrideContent", "c");

    GitStoreConfig gitStoreConfig = GitStore.builder().build();
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder().store(gitStoreConfig).build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(gitStoreDelegateConfig)
        .when(serverlessStepCommonHelper)
        .getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome);
    ServerlessManifestConfig serverlessManifestConfig =
        serverlessAwsLambdaStepHelper.getServerlessManifestConfig(manifestOutcome, ambiance, manifestParams);
    assertThat(((ServerlessAwsLambdaManifestConfig) serverlessManifestConfig).getManifestPath()).isEqualTo("a");
    assertThat(((ServerlessAwsLambdaManifestConfig) serverlessManifestConfig).getGitStoreDelegateConfig())
        .isEqualTo(gitStoreDelegateConfig);
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessManifestConfigFailureTest() {
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();
    serverlessAwsLambdaStepHelper.getServerlessManifestConfig(manifestOutcome, ambiance, new HashMap<>());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getManifestFileContentTest() {
    GitStoreConfig gitStoreConfig = GitStore.builder().build();
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder()
                                          .configOverridePath(ParameterField.createValueField("adsf"))
                                          .store(gitStoreConfig)
                                          .identifier("identifier")
                                          .build();
    GitFile gitFile = GitFile.builder().filePath("filePath").fileContent("fileContent").build();
    List<GitFile> gitFileList = Arrays.asList(gitFile);
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().files(gitFileList).build();
    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<String, FetchFilesResult>() {
      { put("identifier", fetchFilesResult); }
    };
    Optional<Pair<String, String>> manifestFileContent = Optional.of(new MutablePair<>("adsf", "fileContent"));

    assertThat(serverlessAwsLambdaStepHelper.getManifestFileContent(fetchFilesResultMap, manifestOutcome))
        .isEqualTo(manifestFileContent);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerlessDeployFunctionInstanceInfoTest() {
    ServerlessAwsLambdaFunction serverlessAwsLambdaFunction =
        ServerlessAwsLambdaFunction.builder().timeout(10).memorySize("1024").handler("handler").build();
    ServerlessDeployResult serverlessDeployResult = ServerlessAwsLambdaDeployResult.builder()
                                                        .functions(Arrays.asList(serverlessAwsLambdaFunction))
                                                        .stage("stage")
                                                        .region("us-east-2")
                                                        .build();

    ServerlessAwsLambdaServerInstanceInfo serverlessAwsLambdaInstanceInfo =
        (ServerlessAwsLambdaServerInstanceInfo) serverlessAwsLambdaStepHelper
            .getServerlessDeployFunctionInstanceInfo(serverlessDeployResult, "infraKey")
            .get(0);

    assertThat(serverlessAwsLambdaInstanceInfo.getInfraStructureKey()).isEqualTo("infraKey");
    assertThat(serverlessAwsLambdaInstanceInfo.getRegion()).isEqualTo("us-east-2");
    assertThat(serverlessAwsLambdaInstanceInfo.getTimeout()).isEqualTo(10);
    assertThat(serverlessAwsLambdaInstanceInfo.getMemorySize()).isEqualTo("1024");
    assertThat(serverlessAwsLambdaInstanceInfo.getHandler()).isEqualTo("handler");
  }
}