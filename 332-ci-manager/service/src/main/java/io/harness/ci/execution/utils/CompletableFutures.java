/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CompletableFutures<T> {
  private final Executor executor;
  private final List<CompletableFuture<T>> completableFutures = new ArrayList<>();

  public CompletableFutures(Executor executor) {
    this.executor = executor;
  }

  public void supplyAsync(Supplier<T> supplier) {
    completableFutures.add(CompletableFuture.supplyAsync(supplier, executor));
  }

  public void supplyAsyncExceptionally(Supplier<T> supplier, Function<Throwable, ? extends T> throwableFn) {
    completableFutures.add(CompletableFuture.supplyAsync(supplier, executor).exceptionally(throwableFn));
  }

  public CompletableFuture<List<T>> allOf() {
    return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
        .thenApply(ignored -> completableFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }
}
