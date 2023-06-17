/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.PRFile;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ScmPathFilterEvaluationTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private Consumer<DelegateTaskResponse> consumer;
  @Mock private ScmDelegateClient scmDelegateClient;
  @Mock BooleanSupplier booleanSupplier;
  @Mock ScmServiceClient scmServiceClient;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Inject
  @InjectMocks
  private ScmPathFilterEvaluationTask scmPathFilterEvaluationTask = new ScmPathFilterEvaluationTask(
      DelegateTaskPackage.builder().delegateTaskId("delegateTaskId").data(TaskData.builder().build()).build(),
      iLogStreamingTaskClient, consumer, booleanSupplier);
  @Mock SecretDecryptionService secretDecryptionService;
  ScmPathFilterEvaluationTaskParams scmPathFilterEvaluationTaskParams =
      ScmPathFilterEvaluationTaskParams.builder()
          .scmConnector(BitbucketConnectorDTO.builder().url("url").build())
          .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void runBitbucketOnPremTest() throws IOException {
    ScmPathFilterEvaluationTaskParams scmPathFilterEvaluationTaskParams =
        ScmPathFilterEvaluationTaskParams.builder()
            .latestCommit("latest")
            .previousCommit("previous")
            .operator("Equals")
            .standard("file1.txt")
            .scmConnector(BitbucketConnectorDTO.builder().url("url").build())
            .build();
    ScmPathFilterEvaluationTaskParams scmPathFilterEvaluationTaskParams1 =
        ScmPathFilterEvaluationTaskParams.builder()
            .latestCommit("latest")
            .previousCommit("previous")
            .operator("Equals")
            .standard("file1.txt")
            .scmConnector(BitbucketConnectorDTO.builder()
                              .apiAccess(BitbucketApiAccessDTO.builder().build())
                              .url("https://bitbucket.dev.harness.org")
                              .build())
            .build();
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(BitbucketConnectorDTO.builder().build());
    doReturn(CompareCommitsResponse.newBuilder().addFiles(PRFile.newBuilder().setPath("file1.txt").build()).build())
        .when(scmDelegateClient)
        .processScmRequest(any());
    when(scmServiceClient.compareCommits(any(), any(), any(), any()))
        .thenReturn(
            CompareCommitsResponse.newBuilder().addFiles(PRFile.newBuilder().setPath("file1.txt").build()).build());
    assertThat(scmPathFilterEvaluationTask.run(scmPathFilterEvaluationTaskParams1))
        .isEqualTo(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build());
  }
}
