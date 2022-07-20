/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import static io.harness.testframework.framework.utils.ExecutorUtils.addGCVMOptions;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJacocoAgentVM;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJar;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.filesystem.FileIo;
import io.harness.resource.Project;
import io.harness.testframework.framework.utils.ExecutorUtils;
import io.harness.threading.Poller;

import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@Singleton
@Slf4j
public class BatchProcessingExecutor {
  public static final String MODULE = "280-batch-processing";
  public static final String CONFIG_YML = "batch-processing-config.yml";

  private boolean failedAlready;
  private Path livenessMarker;
  private String directoryPath;

  public void ensureBatchProcessing(Class<?> clazz) throws IOException {
    if (!isHealthy()) {
      executeLocalBatchProcessing(clazz);
    }
  }

  private void executeLocalBatchProcessing(Class<?> clazz) throws IOException {
    if (failedAlready) {
      return;
    }
    directoryPath = Project.rootDirectory(clazz);

    final File directory = new File(directoryPath);
    final File lockfile = new File(directoryPath, "batch-processing");

    if (FileIo.acquireLock(lockfile, ofMinutes(2))) {
      try {
        if (isHealthy()) {
          return;
        }
        log.info("Execute the batch-processing from {}", directoryPath);

        final Path jar = ExecutorUtils.getJar(MODULE);
        log.info("The batch-processing jar path is: {}", jar.toAbsolutePath().toString());

        ensureConfigFileExists();

        livenessMarker = getPathFromContext("batch-processing-up");
        // ensure liveness file is deleted.
        FileUtils.deleteQuietly(livenessMarker.toFile());

        for (int i = 0; i < 10; i++) {
          log.info("***");
        }

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xms1024m");

        addGCVMOptions(command);

        command.add("-Dfile.encoding=UTF-8");

        addJacocoAgentVM(jar, command);

        addJar(jar, command);
        command.add("--ensure-timescale=false");
        // yaml config file is read from the root directory, '--config-file' param doesn't make any difference.

        log.info(StringUtils.join(command, " "));

        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.directory(directory);
        processExecutor.command(command);

        processExecutor.redirectOutput(Slf4jStream.of(log).asInfo());
        processExecutor.redirectError(Slf4jStream.of(log).asError());

        final StartedProcess startedProcess = processExecutor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(startedProcess.getProcess()::destroy));
        Poller.pollFor(ofMinutes(5), ofSeconds(2), this::isHealthy);
      } catch (RuntimeException | IOException exception) {
        failedAlready = true;
        throw exception;
      } finally {
        deleteCreatedConfigFile();
        FileUtils.deleteQuietly(livenessMarker.toFile());
        FileIo.releaseLock(lockfile);
      }
    }
  }

  private boolean isHealthy() {
    if (livenessMarker == null) {
      return false;
    }
    File livenessFile = livenessMarker.toFile();
    log.info("Checking for liveness marker {}", livenessFile.getAbsolutePath());
    return livenessFile.exists();
  }

  private void ensureConfigFileExists() {
    Path existingConfigPath = ExecutorUtils.getConfig(directoryPath, MODULE, CONFIG_YML);
    Path expectedConfigPath = getBatchProccessingConfig();

    if (!expectedConfigPath.toFile().exists()) {
      try {
        FileUtils.copyFile(existingConfigPath.toFile(), expectedConfigPath.toFile());
      } catch (IOException e) {
        log.error("Error copying file from {} to dir {}", existingConfigPath.toAbsolutePath(),
            expectedConfigPath.toAbsolutePath(), e);
      }
    }
  }

  private void deleteCreatedConfigFile() {
    Path expectedConfigPath = getBatchProccessingConfig();

    File config = expectedConfigPath.toFile();
    if (config.exists()) {
      config.delete();
    }
  }

  private Path getBatchProccessingConfig() {
    return getPathFromContext(CONFIG_YML);
  }

  private Path getPathFromContext(String... filename) {
    return Paths.get(directoryPath, filename);
  }

  public static void main(String[] args) throws IOException {
    new BatchProcessingExecutor().ensureBatchProcessing(BatchProcessingExecutor.class);
  }
}
