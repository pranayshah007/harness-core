package io.harness.helm;

import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.process.LocalProcessRunner;
import io.harness.process.ProcessRef;
import io.harness.process.ProcessRunner;
import io.harness.process.RunProcessRequest;
import io.harness.process.SharedProcessRunner;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;

@Slf4j
@Singleton
public class HelmCommandRunner {
  private ExecutorService cliExecutorService;
  private ProcessRunner sharedProcessRunner;
  private ProcessRunner localProcessRunner;

  @Inject
  public HelmCommandRunner(@Named("helmCliExecutor") ExecutorService cliExecutorService) {
    this.cliExecutorService = cliExecutorService;
    this.sharedProcessRunner = new SharedProcessRunner(cliExecutorService);
    this.localProcessRunner = new LocalProcessRunner();
  }

  public ProcessResult execute(
      HelmCliCommandType type, String command, String pwd, Map<String, String> envs, long timeoutInMillis) {
    final RunProcessRequest runProcessRequest = RunProcessRequest.builder()
                                                    .pwd(pwd)
                                                    .command(command)
                                                    .environment(envs)
                                                    .timeout(timeoutInMillis)
                                                    .timeoutTimeUnit(TimeUnit.MILLISECONDS)
                                                    .readOutput(true)
                                                    .build();

    switch (type) {
      case REPO_ADD:
      case REPO_UPDATE:
      case REPO_REMOVE:
        return executeShared(type, runProcessRequest);

      default:
        return executeLocal(type, runProcessRequest);
    }
  }

  private ProcessResult executeShared(HelmCliCommandType type, RunProcessRequest request) {
    return runProcessRef(type, sharedProcessRunner.run(request));
  }

  private ProcessResult executeLocal(HelmCliCommandType type, RunProcessRequest request) {
    return runProcessRef(type, localProcessRunner.run(request));
  }

  private ProcessResult runProcessRef(HelmCliCommandType type, ProcessRef processRef) {
    try (processRef) {
      return processRef.get();
    } catch (IOException e) {
      throw new HelmClientException(format("[IO exception] %s", ExceptionUtils.getMessage(e)), USER, type);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientException(format("[Interrupted] %s", ExceptionUtils.getMessage(e)), USER, type);
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new HelmClientException(format("[Timed out] %s", ExceptionUtils.getMessage(e)), USER, type);
    } catch (Exception e) {
      log.error("Unknown error while trying to execute helm command", e);
      throw new HelmClientException(ExceptionUtils.getMessage(e), USER, type);
    }
  }
}
