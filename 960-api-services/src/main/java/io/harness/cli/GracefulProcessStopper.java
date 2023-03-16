/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.stop.ProcessStopper;

@Slf4j
public class GracefulProcessStopper implements ProcessStopper {
  private final long gracefulSecondsTimeout;
  public GracefulProcessStopper(long gracefulSecondsTimeout) {
    this.gracefulSecondsTimeout = gracefulSecondsTimeout;
  }

  @Override
  public void stop(Process process) {
    // We faced an issue when destroying processes with sigterm while in docker container. When we send a sigterm to a
    // parent process, this parent process is not sending properly the signal to child processes. For example: parent
    // process is /bin/sh and child processes are terraform and terraform-provider. So we decided to gracefully destroy
    // child processes firstly and then destroy the parent process.

    List<ProcessHandle> allProcesses = process.descendants().collect(Collectors.toList());
    Deque<ProcessHandle> processHandlers = new ArrayDeque<>(allProcesses);
    Set<Long> pidsOfCompletedProcesses = new HashSet<>();

    ProcessHandle.of(process.pid()).ifPresent(processHandlers::add);

    CompletableFuture<ProcessHandle> processHandleFuture = null;

    while (!processHandlers.isEmpty()) {
      if (processHandleFuture == null) {
        processHandleFuture = destroy(processHandlers.pop());
      } else {
        processHandleFuture = processHandleFuture.thenCompose(_ignore -> {
          pidsOfCompletedProcesses.add(_ignore.pid());
          return destroy(processHandlers.pop());
        });
      }
    }

    if (processHandleFuture == null) {
      process.destroy();
      try {
        process.waitFor(gracefulSecondsTimeout, TimeUnit.SECONDS);
        process.destroyForcibly();
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
      }
      return;
    }

    try {
      processHandleFuture.orTimeout(gracefulSecondsTimeout, TimeUnit.SECONDS).get();
    } catch (InterruptedException e) {
      log.warn(e.getMessage());
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.warn(e.getMessage());
    } finally {
      destroyNotCompletedProcesses(allProcesses, pidsOfCompletedProcesses);
    }
  }

  private void destroyNotCompletedProcesses(List<ProcessHandle> allProcesses, Set<Long> pidsOfCompletedProcesses) {
    for (ProcessHandle proc : allProcesses) {
      if (!pidsOfCompletedProcesses.contains(proc.pid())) {
        proc.destroyForcibly();
      }
    }
  }

  private CompletableFuture<ProcessHandle> destroy(ProcessHandle processHandle) {
    processHandle.destroy();
    return processHandle.onExit();
  }
}