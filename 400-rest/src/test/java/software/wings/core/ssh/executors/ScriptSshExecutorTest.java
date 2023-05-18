/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.rule.OwnerRule.AGORODETKI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;

import software.wings.delegatetasks.DelegateFileManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ScriptSshExecutor.class)
public class ScriptSshExecutorTest extends CategoryTest {
  private static final String ENV_VAR_VALUE = "/some/valid/path";

  @Mock private DelegateFileManager delegateFileManager;
  @Mock private LogCallback logCallback;
  @Mock private SshSessionConfig sshSessionConfig;

  private ScriptSshExecutor scriptSshExecutor;

  @Before
  public void setup() throws Exception {
    when(sshSessionConfig.getExecutionId()).thenReturn("ID");
    scriptSshExecutor = PowerMockito.spy(new ScriptSshExecutor(logCallback, true, sshSessionConfig));
    PowerMockito.doReturn(ENV_VAR_VALUE).when(scriptSshExecutor, "getEnvVarValue", "Path");
    PowerMockito.doReturn(ENV_VAR_VALUE).when(scriptSshExecutor, "getEnvVarValue", "HOME");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRecognizeEnvVarsInPathAndReplaceWithValuesExtractedFromSystem() {
    assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME")).isEqualTo(ENV_VAR_VALUE);
    assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME/abc/$Path"))
        .isEqualTo(ENV_VAR_VALUE + "/abc" + ENV_VAR_VALUE);
    assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME/work$Path/abc"))
        .isEqualTo(ENV_VAR_VALUE + "/work" + ENV_VAR_VALUE + "/abc");
  }
}
