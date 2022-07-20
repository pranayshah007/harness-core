/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import static io.harness.testframework.framework.utils.ExecutorUtils.addConfig;
import static io.harness.testframework.framework.utils.ExecutorUtils.addGCVMOptions;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJacocoAgentVM;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJar;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.join;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filesystem.FileIo;
import io.harness.resource.Project;
import io.harness.threading.Poller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class WatcherExecutor {
  private static boolean failedAlready;
  private static volatile Process watcherProcess;

  public static void ensureWatcher(Class clazz) throws IOException {
    if (!isHealthy()) {
      executeLocalWatcher(clazz);
    }
  }

  private static void executeLocalWatcher(Class clazz) throws IOException {
    if (failedAlready) {
      return;
    }

    String directoryPath = Project.rootDirectory(clazz);
    final File directory = new File(directoryPath);
    final File lockfile = new File(directoryPath, "watcher");

    if (FileIo.acquireLock(lockfile, ofMinutes(2))) {
      try {
        if (isHealthy()) {
          return;
        }
        log.info("Execute the watcher from {}", directory);
        final Path jar = Paths.get(directory.getPath(), "960-watcher", "target", "watcher-capsule.jar");
        final Path config = Paths.get(directory.getPath(), "960-watcher", "config-watcher.yml");

        List<String> command = new ArrayList<>();
        command.add("java");

        addGCVMOptions(command);
        addJacocoAgentVM(jar, command);

        addJar(jar, command);
        addConfig(config, command);

        log.info(join(command, " "));

        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.directory(directory);
        processExecutor.command(command);

        processExecutor.redirectOutput(Slf4jStream.of(log).asInfo());
        processExecutor.redirectError(Slf4jStream.of(log).asError());

        final StartedProcess startedProcess = processExecutor.start();
        watcherProcess = startedProcess.getProcess();
        Runtime.getRuntime().addShutdownHook(new Thread(watcherProcess::destroy));

        Poller.pollFor(ofMinutes(2), ofSeconds(2), WatcherExecutor::isHealthy);

      } catch (RuntimeException exception) {
        failedAlready = true;
        throw exception;
      } finally {
        FileIo.releaseLock(lockfile);
      }
    }
  }

  private static boolean isHealthy() {
    return watcherProcess != null && watcherProcess.isAlive();
  }

  public static void main(String[] args) throws IOException {
    ensureWatcher(WatcherExecutor.class);
  }
}
