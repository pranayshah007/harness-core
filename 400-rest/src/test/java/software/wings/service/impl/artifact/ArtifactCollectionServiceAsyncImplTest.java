/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.HARSH;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream.Action;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ArtifactCollectionServiceAsyncImplTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;

  @Mock private WaitNotifyEngine mockWaitNotifyEngine;

  @Inject @InjectMocks private ArtifactCollectionServiceAsyncImpl artifactCollectionServiceAsync;
  @Mock private BuildSourceService buildSourceService;

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void verifyCustomCollectionWithCustomScriptTimeout() {
    CustomArtifactStream customArtifactStream = createCustomArtifactStream("70");
    when(delegateService.queueTaskV2(any())).thenReturn("ID");
    artifactCollectionServiceAsync.collectNewArtifactsAsync(customArtifactStream, "permitId");
    verify(mockWaitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(argument.capture());
    assertThat(argument.getValue().getData().getTimeout()).isEqualTo(70000);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void verifyCustomCollectionWithDefaultScriptTimeout() {
    CustomArtifactStream customArtifactStream = createCustomArtifactStream("");
    when(delegateService.queueTaskV2(any())).thenReturn("ID");
    artifactCollectionServiceAsync.collectNewArtifactsAsync(customArtifactStream, "permitId");
    verify(mockWaitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(argument.capture());
    assertThat(argument.getValue().getData().getTimeout()).isEqualTo(60000);
  }

  private CustomArtifactStream createCustomArtifactStream(String timeoutInSecs) {
    return CustomArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .name("Custom Artifact Stream - " + System.currentTimeMillis())
        .scripts(asList(CustomArtifactStream.Script.builder()
                            .action(Action.FETCH_VERSIONS)
                            .scriptString("echo Hello World!! and echo ${secrets.getValue(My Secret)}")
                            .timeout(timeoutInSecs)
                            .build()))
        .build();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void collectNewArtifactsForParameterizedArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .appId(APP_ID)
                                                  .accountId(ACCOUNT_ID)
                                                  .name("nexus test")
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("${path"))
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    Map<String, Object> artifactVariables = new HashMap<>();
    artifactVariables.put("path", "todolist");
    artifactVariables.put("buildNo", "1.0");
    artifactCollectionServiceAsync.collectNewArtifacts(APP_ID, nexusArtifactStream, "1.0", artifactVariables);
    verify(buildSourceService, times(1)).getBuild(anyString(), anyString(), any(), any());
  }
}
