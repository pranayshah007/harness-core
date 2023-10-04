/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.spotinst.model.SpotInstConstants.DELETE_NEW_ELASTI_GROUP;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.spot.SpotConfig;
import io.harness.connector.task.spot.SpotNgConfigMapper;
import io.harness.connector.task.spot.SpotPermanentTokenCredential;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ElastigroupRollbackTaskTest extends CategoryTest {
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private SpotNgConfigMapper ngConfigMapper;
  @Mock private ExecutorService executorService;
  @Mock private ElastigroupDeployTaskHelper taskHelper;
  @InjectMocks
  private ElastigroupRollbackTask elastigroupRollbackTask = new ElastigroupRollbackTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), logStreamingTaskClient, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCloseLogStreamInExecuteBasicAndCanaryRollbackThrowsException() throws Exception {
    ElastigroupRollbackTaskParameters parameters = ElastigroupRollbackTaskParameters.builder().blueGreen(false).build();
    doReturn(null).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doThrow(new RuntimeException()).when(taskHelper).cleanupNewElastigroups(anyBoolean(), any(), any(), any(), any());
    when(ngConfigMapper.mapSpotConfigWithDecryption(any(), any()))
        .thenReturn(
            SpotConfig.builder()
                .credential(
                    SpotPermanentTokenCredential.builder().spotAccountId("accountID").appTokenId("token").build())
                .build());
    assertThatThrownBy(() -> elastigroupRollbackTask.run(parameters)).isInstanceOf(RuntimeException.class);
    verify(logStreamingTaskClient, times(1)).closeStream(DELETE_NEW_ELASTI_GROUP);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCloseLogStreamInBlueGreenSetupRollbackThrowsExceptionInDeletion() throws Exception {
    ElastigroupRollbackTaskParameters parameters = ElastigroupRollbackTaskParameters.builder().blueGreen(true).build();
    doReturn(null).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doNothing().when(taskHelper).cleanupNewElastigroups(anyBoolean(), any(), any(), any(), any());
    doThrow(new RuntimeException()).when(taskHelper).deleteElastigroup(any(), any(), any(), any());
    when(ngConfigMapper.mapSpotConfigWithDecryption(any(), any()))
        .thenReturn(
            SpotConfig.builder()
                .credential(
                    SpotPermanentTokenCredential.builder().spotAccountId("accountID").appTokenId("token").build())
                .build());
    assertThatThrownBy(() -> elastigroupRollbackTask.run(parameters)).isInstanceOf(RuntimeException.class);
    verify(logStreamingTaskClient, times(1)).closeStream(DELETE_NEW_ELASTI_GROUP);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCloseLogStreamInBlueGreenSetupRollbackThrowsException() throws Exception {
    ElastigroupRollbackTaskParameters parameters = ElastigroupRollbackTaskParameters.builder().blueGreen(true).build();
    doReturn(null).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doThrow(new RuntimeException()).when(taskHelper).cleanupNewElastigroups(anyBoolean(), any(), any(), any(), any());
    when(ngConfigMapper.mapSpotConfigWithDecryption(any(), any()))
        .thenReturn(
            SpotConfig.builder()
                .credential(
                    SpotPermanentTokenCredential.builder().spotAccountId("accountID").appTokenId("token").build())
                .build());
    assertThatThrownBy(() -> elastigroupRollbackTask.run(parameters)).isInstanceOf(RuntimeException.class);
    verify(logStreamingTaskClient, times(1)).closeStream(DELETE_NEW_ELASTI_GROUP);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCloseLogStreamInBlueGreenSetupRollback() {
    ElastigroupRollbackTaskParameters parameters = ElastigroupRollbackTaskParameters.builder().blueGreen(true).build();
    doReturn(null).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    when(ngConfigMapper.mapSpotConfigWithDecryption(any(), any()))
        .thenReturn(
            SpotConfig.builder()
                .credential(
                    SpotPermanentTokenCredential.builder().spotAccountId("accountID").appTokenId("token").build())
                .build());
    elastigroupRollbackTask.run(parameters);
    verify(logStreamingTaskClient, times(1)).closeStream(DELETE_NEW_ELASTI_GROUP);
  }
}
