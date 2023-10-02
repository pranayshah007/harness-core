/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.progresstaskstreaming;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.helm.HelmDeployProgressData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class NGDelegateTaskProgressCallbackTest extends CategoryTest {
  @Mock private ITaskProgressClient taskProgressClient;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendTaskProgressUpdate() {
    DelegateProgressData delegateProgressData = HelmDeployProgressData.builder().build();
    String taskId = "qwertyuioppoiuytrewq";
    NGDelegateTaskProgressCallback ngDelegateTaskProgressCallback =
        new NGDelegateTaskProgressCallback(logStreamingTaskClient, taskId);
    doReturn(taskProgressClient).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doThrow(new RuntimeException("failed")).when(taskProgressClient).sendTaskProgressUpdate(delegateProgressData);
    ngDelegateTaskProgressCallback.sendTaskProgressUpdate("Ut test", delegateProgressData);
    verify(taskProgressClient, Mockito.times(0)).sendTaskProgressUpdate(delegateProgressData);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendTaskProgressUpdateWhenLogStreamingClientNull() {
    DelegateProgressData delegateProgressData = HelmDeployProgressData.builder().build();
    String taskId = "qwertyuioppoiuytrewq";
    NGDelegateTaskProgressCallback ngDelegateTaskProgressCallback = new NGDelegateTaskProgressCallback(null, taskId);
    ngDelegateTaskProgressCallback.sendTaskProgressUpdate("Ut test", delegateProgressData);
    verify(logStreamingTaskClient, Mockito.times(0)).obtainTaskProgressClient();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendTaskProgressUpdateWhenTaskProgressClientNull() {
    DelegateProgressData delegateProgressData = HelmDeployProgressData.builder().build();
    String taskId = "qwertyuioppoiuytrewq";
    NGDelegateTaskProgressCallback ngDelegateTaskProgressCallback =
        new NGDelegateTaskProgressCallback(logStreamingTaskClient, taskId);
    doReturn(null).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    ngDelegateTaskProgressCallback.sendTaskProgressUpdate("Ut test", delegateProgressData);
    verify(taskProgressClient, Mockito.times(0)).sendTaskProgressUpdate(delegateProgressData);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendTaskProgressUpdateWhenProgressDataNull() {
    DelegateProgressData delegateProgressData = null;
    String taskId = "qwertyuioppoiuytrewq";
    NGDelegateTaskProgressCallback ngDelegateTaskProgressCallback =
        new NGDelegateTaskProgressCallback(logStreamingTaskClient, taskId);
    doReturn(taskProgressClient).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    ngDelegateTaskProgressCallback.sendTaskProgressUpdate("Ut test", delegateProgressData);
    verify(logStreamingTaskClient, Mockito.times(1)).obtainTaskProgressClient();
    verify(logStreamingTaskClient, Mockito.times(1)).obtainTaskProgressExecutor();
    verify(taskProgressClient, Mockito.times(0)).sendTaskProgressUpdate(delegateProgressData);
  }
}
