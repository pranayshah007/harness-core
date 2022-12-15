/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.agent;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@Slf4j
public class DelegateAgent {
  // TODO(gauravnanda): Parametrize this.
  private static final String DELEGATE_RUNNER_JAR_PATH = "/Users/gauravnanda/test-jar/TaskRunner.jar";
  private static final int TIMEOUT_MINUTES = 15;

  public static void main(String[] args) throws IOException, InterruptedException, TimeoutException {
    log.info("Delegate agent started");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      // TODO(gauravnanda): Reply back to delegate if not already replied to delegate. We will probably need to build
      // some state tracking about if a reply has already been sent back.
      log.debug("Shutdown hook triggered");
    }));

    ProcessResult runnerExecutionResult = runDelegateRunner(DELEGATE_RUNNER_JAR_PATH);
    // TODO(gauravnanda): Verify the return type from the runner.
    // TODO(gauravnanda): Add logic to call delegate endpoint to return result.
    if (runnerExecutionResult.getExitValue() == 0) {
      log.info(runnerExecutionResult.outputUTF8());
    } else {
      log.error(runnerExecutionResult.outputUTF8());
    }

    //
  }

  /**
   * Execute delegate runner jar file
   * @param runnerJarPath path of the jar file to execute.
   * @return process execution result.
   * @throws IOException
   * @throws InterruptedException
   * @throws TimeoutException
   */
  private static ProcessResult runDelegateRunner(String runnerJarPath)
      throws IOException, InterruptedException, TimeoutException {
    final ProcessExecutor processExecutor = new ProcessExecutor()
                                                .timeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                                                .command("java", "-jar", runnerJarPath)
                                                .readOutput(true);

    return processExecutor.execute();
  }
}
