/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import static io.harness.delegate.clienttools.ClientTool.SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.clienttools.ScmVersion;

import io.grpc.ManagedChannel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public abstract class ScmUnixManager implements AutoCloseable {
  abstract ManagedChannel getChannel();
  abstract void shutdownEventLoopGroup();

  private final String PATH_TO_SCM_BUILD =
      Paths.get(InstallUtils.getPath(SCM, ScmVersion.DEFAULT)).getParent().toString();

  protected String socketAddress;
  protected ProcessExecutor processBuilder;
  protected StartedProcess server;

  public ScmUnixManager() throws IOException {
    socketAddress = "/tmp/" + UUIDGenerator.generateUuid();
    processBuilder = new ProcessExecutor();
    runServer();
  }

  public void close() throws IOException {
    server.getProcess().destroy();
    shutdownEventLoopGroup();
    processBuilder.command("rm", "-rf", socketAddress);
    final StartedProcess process = processBuilder.start();
    process.getProcess().destroy();
  }

  private void runServer() throws IOException {
    processBuilder.directory(new File(PATH_TO_SCM_BUILD));
    processBuilder.command("./" + SCM.getBinaryName(), "--unix", socketAddress);
    log.info("Running SCM server at path: {} on port: {}", PATH_TO_SCM_BUILD, socketAddress);
    processBuilder.redirectOutput(System.out);
    processBuilder.redirectError(System.err);
    server = processBuilder.start();
  }
}
