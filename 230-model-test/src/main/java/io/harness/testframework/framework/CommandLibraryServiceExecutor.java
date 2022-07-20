/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import static io.harness.filesystem.FileIo.acquireLock;
import static io.harness.testframework.framework.utils.ExecutorUtils.addConfig;
import static io.harness.testframework.framework.utils.ExecutorUtils.addGCVMOptions;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJacocoAgentVM;
import static io.harness.testframework.framework.utils.ExecutorUtils.addJar;

import static io.restassured.config.HttpClientConfig.httpClientConfig;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.filesystem.FileIo;
import io.harness.project.Alpn;
import io.harness.resource.Project;
import io.harness.threading.Poller;

import com.google.inject.Singleton;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.params.HttpConnectionParams;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessExecutor;

@Singleton
@Slf4j
public class CommandLibraryServiceExecutor {
  public static final String COMMAND_LIBRARY_SERVER_MODULE = "210-command-library-server";
  private boolean hasFailed;

  public void ensureCommandLibraryService(Class clazz) throws IOException {
    if (!isHealthy()) {
      executeLocalCommandLibraryService(clazz);
    }
  }

  private void executeLocalCommandLibraryService(Class clazz) throws IOException {
    if (hasFailed) {
      return;
    }

    final String rootDirectoryPath = Project.rootDirectory(clazz);
    final File lockfile = getLockfile(rootDirectoryPath);

    if (acquireLock(lockfile, ofMinutes(2))) {
      try {
        if (isHealthy()) {
          log.info("Command Library Service is healthy");
          return;
        }
        final File rootDirectory = new File(rootDirectoryPath);
        log.info("Execute the command library service from {}", rootDirectory);
        final Path jar = Paths.get(
            rootDirectory.getPath(), COMMAND_LIBRARY_SERVER_MODULE, "target", "command-library-app-capsule.jar");
        final Path config =
            Paths.get(rootDirectory.getPath(), COMMAND_LIBRARY_SERVER_MODULE, "command-library-server-config.yml");

        String alpn = Alpn.location();

        for (int i = 0; i < 10; i++) {
          log.info("****");
        }

        List<String> command = new ArrayList<>();
        addJvmParams(alpn, command);
        addJacocoAgentVM(jar, command);
        addJar(jar, command);
        addConfig(config, command);
        log.info(String.join(" ", command));

        startServiceProcess(rootDirectory, command);

        Poller.pollFor(ofMinutes(2), ofSeconds(2), this::isHealthy);
      } catch (RuntimeException | IOException exception) {
        hasFailed = true;
        throw exception;
      } finally {
        FileIo.releaseLock(lockfile);
      }
    }
  }

  @NotNull
  private File getLockfile(String rootDirectoryPath) {
    return new File(rootDirectoryPath, "command-library-service");
  }

  private void addJvmParams(String alpn, List<String> command) {
    command.add("java");
    command.add("-Xms1024m");
    addGCVMOptions(command);
    command.add("-Dfile.encoding=UTF-8");
    command.add("-Xbootclasspath/p:" + alpn);
  }

  private void startServiceProcess(File directory, List<String> command) throws IOException {
    ProcessExecutor processExecutor = new ProcessExecutor();
    processExecutor.command(command);
    processExecutor.directory(directory);

    processExecutor.redirectOutput(out);
    processExecutor.redirectError(err);

    processExecutor.start();
  }

  private Exception previous = new Exception();

  private boolean isHealthy() {
    try {
      RestAssuredConfig config =
          RestAssured.config().httpClient(httpClientConfig()
                                              .setParam(HttpConnectionParams.CONNECTION_TIMEOUT, 5000)
                                              .setParam(HttpConnectionParams.SO_TIMEOUT, 5000));

      Setup.commandLibraryService().config(config).when().get("/health").then().statusCode(HttpStatus.SC_OK);
    } catch (Exception exception) {
      if (exception.getMessage().equals(previous.getMessage())) {
        log.info("not healthy");
      } else {
        log.info("not healthy - {}", exception.getMessage());
        previous = exception;
      }
      return false;
    }
    log.info("healthy");
    return true;
  }
}
