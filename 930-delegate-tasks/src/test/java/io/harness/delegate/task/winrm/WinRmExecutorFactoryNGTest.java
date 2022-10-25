/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.core.winrm.executors.WinRmExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class WinRmExecutorFactoryNGTest extends CategoryTest {
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock LogCallback logCallback;
  @Mock WinRmSessionConfig config;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CommandUnitsProgress commandUnitsProgress;

  @InjectMocks WinRmExecutorFactoryNG winRmExecutorFactoryNG;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutor() {
    WinRmExecutor winRmExecutor =
        winRmExecutorFactoryNG.getExecutor(config, false, false, logStreamingTaskClient, commandUnitsProgress);
    assertThat(winRmExecutor).isInstanceOf(DefaultWinRmExecutor.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFiledBasedWinRmExecutor() {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG =
        winRmExecutorFactoryNG.getFiledBasedWinRmExecutor(config, false, logStreamingTaskClient, commandUnitsProgress);
    assertThat(fileBasedWinRmExecutorNG).isInstanceOf(FileBasedWinRmExecutorNG.class);
  }
}
