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
import static io.harness.testframework.framework.utils.ExecutorUtils.getConfig;
import static io.harness.testframework.framework.utils.ExecutorUtils.getJar;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.join;

import io.harness.filesystem.FileIo;
import io.harness.project.Alpn;
import io.harness.resource.Project;
import io.harness.threading.Poller;

import com.google.inject.Singleton;
import io.grpc.Channel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

/**
 * Start Event service as part of functional tests
 */
@Singleton
@Slf4j
public class EventServerExecutor {
  public static final String MODULE = "350-event-server";
  public static final String CONFIG_YML = "event-service-config.yml";
  private boolean failedAlready;

  @Getter(lazy = true) private final Channel channel = makeChannel();

  public void ensureEventServer(Class<?> clazz) throws IOException {
    if (!isHealthy()) {
      executeLocalEventServer(clazz);
    }
  }

  private void executeLocalEventServer(Class<?> clazz) throws IOException {
    if (failedAlready) {
      return;
    }
    String directoryPath = Project.rootDirectory(clazz);
    final File directory = new File(directoryPath);
    final File lockfile = new File(directoryPath, "event-server");

    if (FileIo.acquireLock(lockfile, ofMinutes(2))) {
      try {
        if (isHealthy()) {
          return;
        }

        log.info("Execute the event-server from {}", directory);
        final Path jar = getJar(MODULE);
        log.info("The event-server jar path is: {}", jar.toString());

        final Path config = getConfig(directory.getPath(), MODULE, CONFIG_YML);
        String alpn = Alpn.location();

        for (int i = 0; i < 10; i++) {
          log.info("***");
        }

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xms1024m");

        addGCVMOptions(command);

        command.add("-Dfile.encoding=UTF-8");
        command.add("-Xbootclasspath/p:" + alpn);

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
        Runtime.getRuntime().addShutdownHook(new Thread(startedProcess.getProcess()::destroy));
        Poller.pollFor(ofMinutes(2), ofSeconds(2), this::isHealthy);
      } catch (RuntimeException | IOException exception) {
        failedAlready = true;
        throw exception;
      } finally {
        FileIo.releaseLock(lockfile);
      }
    }
  }

  @SneakyThrows
  private Channel makeChannel() {
    SslContext sslContext = GrpcSslContexts.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    return NettyChannelBuilder.forTarget("localhost:9890").sslContext(sslContext).build();
  }
  private Exception previous = new Exception();

  private boolean isHealthy() {
    try {
      if (HealthGrpc.newBlockingStub(getChannel()).check(HealthCheckRequest.newBuilder().build()).getStatus()
          != ServingStatus.SERVING) {
        return false;
      }
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
