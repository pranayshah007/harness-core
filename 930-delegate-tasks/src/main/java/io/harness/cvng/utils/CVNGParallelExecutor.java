/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.UnexpectedException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class CVNGParallelExecutor {
  @Inject @Named("cvngParallelExecutor") protected ExecutorService executorService;

  public <T> List<T> executeParallel(List<Callable<T>> callables) {
    CompletionService<T> completionService = new ExecutorCompletionService<>(executorService);
    log.debug("Parallelizing {} callables", callables.size());
    for (Callable<T> callable : callables) {
      completionService.submit(callable::call);
    }

    List<T> rv = new ArrayList<>();
    for (int i = 0; i < callables.size(); i++) {
      try {
        Future<T> poll = completionService.poll(3, TimeUnit.MINUTES);
        if (poll != null && poll.isDone()) {
          T result = poll.get();
          rv.add(result);
        } else {
          log.info("Timeout. Execution took longer than 1 minutes {}", callables);
          // TODO: Set monitoring/alert if this happens. We should be using Mongo timeout on queries.
          // Something like: new FindOptions().maxTime(5, TimeUnit.SECONDS);
          // This timeout also cancels queries on the mongo server so database resources are also freed up.
          throw new TimeoutException("Timeout. Execution took longer than 1 minutes.  ");
        }
      } catch (ExecutionException ee) {
        throw new UnexpectedException("Error executing task " + ee.getMessage(), ee);
      } catch (Exception e) {
        throw new UnexpectedException("Error executing task " + e.getMessage(), e);
      }
    }
    log.debug("Done parallelizing callables {} ", callables.size());
    return rv;
  }
}
