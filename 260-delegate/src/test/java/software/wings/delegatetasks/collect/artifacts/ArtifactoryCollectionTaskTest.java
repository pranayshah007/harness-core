/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactoryCollectionTaskParameters;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Created by sgurubelli on 10/1/17.
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactoryCollectionTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ArtifactoryService artifactoryService;
  @Mock EncryptionService encryptionService;

  String url = "http://localhost:8881/artifactory/";

  private ArtifactoryConfig artifactoryConfig =
      ArtifactoryConfig.builder().artifactoryUrl(url).username("admin").password("dummy123!".toCharArray()).build();
  private ArtifactoryCollectionTaskParameters artifactoryCollectionTaskParameters =
      ArtifactoryCollectionTaskParameters.builder()
          .artifactoryConfig(artifactoryConfig)
          .jobName("harness-maven")
          .metadata(ImmutableMap.of("buildNo", "1.1"))
          .build();
  private TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .taskType(TaskType.ARTIFACTORY_COLLECTION.name())
                                  .parameters(new Object[] {artifactoryCollectionTaskParameters})
                                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                  .build();

  @InjectMocks
  private ArtifactoryCollectionTask artifactoryCollectionTask =
      new ArtifactoryCollectionTask(DelegateTaskPackage.builder().delegateId("delid1").data(taskData).build(), null,
          notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCollectNoMavenStyleFiles() {
    ListNotifyResponseData res = ListNotifyResponseData.Builder.aListNotifyResponseData().build();
    when(artifactoryService.downloadArtifacts(any(ArtifactoryConfigRequest.class), any(), any(), any(), any(), any()))
        .thenReturn(res);
    res = artifactoryCollectionTask.run(taskData.getParameters());
    assertThat(res).isNotNull();
  }
}
