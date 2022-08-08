/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.winrm.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.DINESH;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.beans.ConfigFile;
import software.wings.core.ssh.executors.FileBasedWinRmExecutor;
import software.wings.delegatetasks.DelegateFileManager;

import java.util.ArrayList;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class FileBasedWinRmExecutorTest extends CategoryTest {
  @Mock FileBasedWinRmExecutor fileBasedWinRmExecutor;
  @Mock LogCallback logCallback;
  @Mock WinRmSessionConfig config;
  @Mock DelegateFileManager delegateFileManager;
  private ConfigFile configFile = ConfigFile.builder().encrypted(false).entityId("TEST_ID").build();
  private ConfigFileMetaData configFileMetaData = ConfigFileMetaData.builder()
                                                      .destinationDirectoryPath("TEST_PATH")
                                                      .fileId(configFile.getUuid())
                                                      .filename("TEST_FILE_NAME")
                                                      .length(configFile.getSize())
                                                      .encrypted(configFile.isEncrypted())
                                                      .activityId("TEST_ACTIVITY_ID")
                                                      .build();

  private FileBasedWinRmExecutor spyFileBasedWinRmExecutor;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    spyFileBasedWinRmExecutor = new FileBasedWinRmExecutor(logCallback, delegateFileManager, true, config, true);
  }

  @Test
  @Owner(developers = DINESH)
  @Category(UnitTests.class)
  public void shouldCopyConfigFile() {
    doReturn(CommandExecutionStatus.SUCCESS).when(fileBasedWinRmExecutor).copyConfigFiles(configFileMetaData);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCopyFiles() {
    assertThatThrownBy(() -> spyFileBasedWinRmExecutor.copyFiles("", new ArrayList<>()))
        .isInstanceOf(NotImplementedException.class)
        .hasMessageContaining(FileBasedWinRmExecutor.NOT_IMPLEMENTED);
  }
}
