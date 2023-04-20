/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.shell.ChildProcessStopper;

import software.wings.WingsBaseTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;

@OwnedBy(CDC)
public class ChildProcessExecutorTest extends WingsBaseTest {
  private String FILE_NAME = "FILE";
  private File workingDirectory = new File(System.getProperty("user.dir"));
  private ProcessExecutor processExecutor = Mockito.mock(ProcessExecutor.class);
  private ChildProcessStopper childProcessStopper =
      new ChildProcessStopper(FILE_NAME, workingDirectory, processExecutor);
  private ListAppender<ILoggingEvent> listAppender;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteScript() throws IllegalAccessException {
    Process process = mock(Process.class);
    Logger logger = (Logger) LoggerFactory.getLogger(ChildProcessStopper.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    childProcessStopper.stop(process);
    Mockito.verify(processExecutor).command("/bin/bash", "kill-" + FILE_NAME);
    assertThat(listAppender.list.get(0).getFormattedMessage())
        .isEqualTo(String.format("Kill child processes command: %s",
            "list_descendants ()\n"
                + "{\n"
                + "  local children=$(ps -ef | grep -v grep | grep $1 | awk '{print $2}')\n"
                + "  kill -9 ${children[0]}\n"
                + "\n"
                + "  for (( c=1; c<${#children[@]} ; c++ ))\n"
                + "  do\n"
                + "    list_descendants ${children[c]}\n"
                + "  done\n"
                + "}\n"
                + "\n"
                + "list_descendants $(ps -ef | grep -v grep | grep -m1 FILE | awk '{print $2}')"));
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
  }
}
