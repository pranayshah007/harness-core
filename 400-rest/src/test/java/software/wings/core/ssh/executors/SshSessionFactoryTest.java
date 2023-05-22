/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.shell.SshSessionFactory.getCopyOfKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionFactory;

import com.jcraft.jsch.JSchException;
import java.io.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class SshSessionFactoryTest extends CategoryTest {
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void getKeyPath() {
    SshSessionConfig config = SshSessionConfig.Builder.aSshSessionConfig().build();
    String homeDir = System.getProperty("user.home");
    String expectedString = homeDir + File.separator + ".ssh" + File.separator + "id_rsa";
    assertThat(SshSessionFactory.getKeyPath(config)).isEqualTo(expectedString);

    config = SshSessionConfig.Builder.aSshSessionConfig().withKeyPath("ExpectedKeyPath").build();
    assertThat(SshSessionFactory.getKeyPath(config)).isEqualTo("ExpectedKeyPath");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void cloneKey() {
    char[] src = "--BEGIN PRIVATE KEY abc --".toCharArray();
    char[] copySrc = getCopyOfKey(SshSessionConfig.Builder.aSshSessionConfig().withKey(src).build());
    assertThat(String.valueOf(copySrc)).isEqualTo("--BEGIN PRIVATE KEY abc --");
    assertThat(copySrc != src);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getSSHSessionThrowsException() {
    final SshSessionConfig config = SshSessionConfig.Builder.aSshSessionConfig().build();
    final LogCallback logCallback = new NoopExecutionCallback();
    assertThatThrownBy(() -> SshSessionFactory.getSSHSession(config, logCallback)).isInstanceOf(JSchException.class);
  }
}
