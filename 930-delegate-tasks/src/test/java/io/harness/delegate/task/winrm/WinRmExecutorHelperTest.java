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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class WinRmExecutorHelperTest extends CategoryTest {
  @Mock WinRmSession winRmSession;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutor() {
    String result = WinRmExecutorHelper.getScriptExecutingCommand("psScriptFile", "powershell");
    assertThat(result).isEqualTo("powershell -f \"psScriptFile\" ");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testConstructPSScriptWithCommandsBulk() {
    String cmd = "text $a $b &a \n zxc";
    List<String> result = WinRmExecutorHelper.constructPSScriptWithCommandsBulk(
        cmd, "psScriptFile", "powershell", Collections.emptyList());
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0)).contains("`$a `$b &a `n zxc`r`n");
    assertThat(result.get(1)).isEqualTo("powershell -f \"psScriptFile\" ");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCleanupFiles() {
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());
    WinRmExecutorHelper.cleanupFiles(winRmSession, "file", "powershell", false, Collections.emptyList());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCleanupFiles_throws_RuntimeException() {
    doThrow(new RuntimeException("test"))
        .when(winRmSession)
        .executeCommandString(anyString(), any(), any(), anyBoolean());
    assertThatThrownBy(
        () -> WinRmExecutorHelper.cleanupFiles(winRmSession, "file", "powershell", false, Collections.emptyList()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("test");
  }
}
