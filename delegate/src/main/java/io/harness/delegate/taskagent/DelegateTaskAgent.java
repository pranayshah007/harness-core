/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.taskagent;

import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClient;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClientFactory;
import io.harness.delegate.taskagent.servicediscovery.ServiceDiscovery;
import io.harness.security.TokenGenerator;
import io.harness.serializer.YamlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.LogOutputStream;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.listener.ShutdownHookProcessDestroyer;

@Slf4j
public class DelegateTaskAgent {
  // TODO(gauravnanda): Parametrize this.
  private static final String DELEGATE_RUNNER_JAR_PATH = "/opt/harness/task.jar";
  private static final String DELEGATE_NAME = System.getenv("DELEGATE_NAME");
  private static final String ACCOUNT_ID = System.getenv("ACCOUNT_ID");
  private static final String DELEGATE_TOKEN = System.getenv("DELEGATE_TOKEN");
  private static final String TASK_ID = System.getenv("TASK_ID");

  private final DelegateCoreClient delegateCoreClient;

  public DelegateTaskAgent(final DelegateCoreClientFactory clientFactory) {
    this.delegateCoreClient =
        clientFactory.createDelegateCoreClient(ServiceDiscovery.getDelegateServiceEndpoint(DELEGATE_NAME));
  }

  public static void main(final String[] args) throws IOException, InterruptedException, TimeoutException {
    try {
      log.info("Delegate agent started");

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // TODO(gauravnanda): Reply back to delegate if not already replied to delegate. We will probably need to build
        // some state tracking about if a reply has already been sent back.
        log.debug("Shutdown hook triggered");
      }));

      final ProcessResult runnerExecutionResult = runDelegateRunner(DELEGATE_RUNNER_JAR_PATH);
      // TODO(gauravnanda): Verify the return type from the runner.
      // TODO(gauravnanda): Add logic to call delegate endpoint to return result.
      if (runnerExecutionResult.getExitValue() == 0) {
        log.info(runnerExecutionResult.outputUTF8());
      } else {
        log.error(runnerExecutionResult.outputUTF8());
      }

      // FIXME: DelegateCoreClientFactory takes kryo converter just temporily until proto response PR merges. This
      // way just to make the compilation pass.
      final var tokenGenerator = new TokenGenerator(ACCOUNT_ID, DELEGATE_TOKEN);
      final var delegateTaskAgent = new DelegateTaskAgent(new DelegateCoreClientFactory(null, tokenGenerator));

      // TODO: We can also use process output rather than writing and reading from file.
      final String outputYaml = Files.readString(Paths.get("/etc/output/result.yaml"));
      final DelegateTaskResponse taskResponse = new YamlUtils().read(outputYaml, DelegateTaskResponse.class);

      delegateTaskAgent.sendResponse(taskResponse);
    } catch (final Exception e) {
      log.error("Something failed!!!", e);
    }
  }

  public void sendResponse(final DelegateTaskResponse taskResponse) throws IOException {
    delegateCoreClient.taskResponse(TASK_ID, taskResponse).execute();
  }

  /**
   * Execute delegate runner jar file
   * @param runnerJarPath path of the jar file to execute.
   * @return process execution result.
   * @throws IOException
   * @throws InterruptedException
   * @throws TimeoutException
   */
  private static ProcessResult runDelegateRunner(final String runnerJarPath)
      throws IOException, InterruptedException, TimeoutException {
    final ProcessExecutor processExecutor = new ProcessExecutor()
                                                .command("java", "-jar", runnerJarPath)
                                                .addDestroyer(ShutdownHookProcessDestroyer.INSTANCE)
                                                .readOutput(true)
                                                .redirectErrorAlsoTo(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String s, final int i) {
                                                    log.error(s);
                                                  }
                                                })
                                                .redirectOutputAlsoTo(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String s, final int i) {
                                                    log.info(s);
                                                  }
                                                });

    return processExecutor.execute();
  }
}
