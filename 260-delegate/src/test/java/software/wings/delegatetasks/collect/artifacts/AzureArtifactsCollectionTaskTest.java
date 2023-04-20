/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureArtifactsCollectionTaskTest extends CategoryTest {
  private static final String DELEGATE_ID1 = "DELEGATE_ID1";
  private static final String DELEGATE_ID2 = "DELEGATE_ID2";
  private static final String VERSION_ID = "VERSION_ID";
  private static final String VERSION = "VERSION";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AzureArtifactsService azureArtifactsService;

  private String azureDevopsUrl = "http://localhost:8881/artifactory/";
  private AzureArtifactsPATConfig azureArtifactsPATConfig =
      AzureArtifactsPATConfig.builder().azureDevopsUrl(azureDevopsUrl).pat("dummy123!".toCharArray()).build();
  private TaskData mavenDelegateTask = prepareTaskData(AzureArtifactsArtifactStreamProtocolType.maven);
  private TaskData nugetDelegateTask = prepareTaskData(AzureArtifactsArtifactStreamProtocolType.nuget);

  @InjectMocks
  private AzureArtifactsCollectionTask mavenCollectionTask = new AzureArtifactsCollectionTask(
      DelegateTaskPackage.builder().accountId(ACCOUNT_ID).delegateId(DELEGATE_ID1).data(mavenDelegateTask).build(),
      null, notifyResponseData -> {}, () -> true);

  @InjectMocks
  private AzureArtifactsCollectionTask nugetCollectionTask = new AzureArtifactsCollectionTask(
      DelegateTaskPackage.builder().accountId(ACCOUNT_ID).delegateId(DELEGATE_ID2).data(nugetDelegateTask).build(),
      null, notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCollectMavenArtifact() {
    mavenCollectionTask.run(mavenDelegateTask.getParameters());
    verify(azureArtifactsService, times(1))
        .downloadArtifact(
            any(AzureArtifactsConfig.class), any(), any(), any(), eq(DELEGATE_ID1), any(), eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCollectMavenArtifactAsTaskParameter() {
    mavenCollectionTask.run((TaskParameters) mavenDelegateTask.getParameters()[0]);
    verify(azureArtifactsService, times(1))
        .downloadArtifact(
            any(AzureArtifactsConfig.class), any(), any(), any(), eq(DELEGATE_ID1), any(), eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCollectNugetArtifact() {
    nugetCollectionTask.run(nugetDelegateTask.getParameters());
    verify(azureArtifactsService, times(1))
        .downloadArtifact(
            any(AzureArtifactsConfig.class), any(), any(), any(), eq(DELEGATE_ID2), any(), eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCollectNugetArtifactAsTaskParameter() {
    nugetCollectionTask.run((TaskParameters) nugetDelegateTask.getParameters()[0]);
    verify(azureArtifactsService, times(1))
        .downloadArtifact(
            any(AzureArtifactsConfig.class), any(), any(), any(), eq(DELEGATE_ID2), any(), eq(ACCOUNT_ID), any());
  }

  private TaskData prepareTaskData(AzureArtifactsArtifactStreamProtocolType protocolType) {
    return TaskData.builder()
        .async(true)
        .taskType(TaskType.AZURE_ARTIFACTS_COLLECTION.name())
        .parameters(new Object[] {
            AzureArtifactsCollectionTaskParameters.builder()
                .accountId(ACCOUNT_ID)
                .azureArtifactsConfig(azureArtifactsPATConfig)
                .encryptedDataDetails(null)
                .artifactStreamAttributes(
                    ArtifactStreamAttributes.builder()
                        .protocolType(protocolType.name())
                        .project("PROJECT")
                        .feed("FEED")
                        .packageId("PACKAGE_ID")
                        .packageName(protocolType == AzureArtifactsArtifactStreamProtocolType.nuget
                                ? "PACKAGE_NAME"
                                : "GROUP_ID:ARTIFACT_ID")
                        .build())
                .artifactMetadata(ImmutableMap.of("buildNo", VERSION, "version", VERSION, "versionId", VERSION_ID))
                .build()})
        .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
        .build();
  }
}
