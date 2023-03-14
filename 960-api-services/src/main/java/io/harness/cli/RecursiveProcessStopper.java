/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.zeroturnaround.exec.stop.ProcessStopper;

public class RecursiveProcessStopper implements ProcessStopper {
  private int secondsToWaitForGracefulShutdown = 30; // TODO Constructuro

  @Override
  public void stop(Process process) {
    Deque<ProcessHandle> processHandlers = process.descendants().collect(Collectors.toCollection(ArrayDeque::new));
    ProcessHandle.of(process.pid()).ifPresent(processHandlers::add);

    CompletableFuture<ProcessHandle> finalFuture = null;

    while (!processHandlers.isEmpty()) {
      if (finalFuture == null) {
        finalFuture = destroy(processHandlers.pop());
      } else {
        finalFuture = finalFuture.thenCompose(_ignore -> destroy(processHandlers.pop()));
      }
    }

    if (finalFuture == null) {
      process.destroy();
      try {
        process.waitFor(secondsToWaitForGracefulShutdown, TimeUnit.SECONDS);
        process.destroyForcibly();
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
      }

      return;
    }

    try {
      finalFuture.orTimeout(secondsToWaitForGracefulShutdown, TimeUnit.SECONDS).get();
    } catch (InterruptedException e) {
      processHandlers.forEach(ProcessHandle::destroyForcibly);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      processHandlers.forEach(ProcessHandle::destroyForcibly);
    }
  }

  private CompletableFuture<ProcessHandle> destroy(ProcessHandle processHandle) {
    processHandle.destroy();
    return processHandle.onExit();
  }
}
