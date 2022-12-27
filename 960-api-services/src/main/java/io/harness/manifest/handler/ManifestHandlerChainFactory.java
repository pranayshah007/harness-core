/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@OwnedBy(CDP)
public abstract class ManifestHandlerChainFactory<S> {
  private final S initialChainState;

  private ManifestHandler currentHandler;
  private ManifestHandler firstHandler;

  public ManifestHandlerChainFactory(S initialChainState) {
    this.initialChainState = initialChainState;
  }

  public abstract ManifestHandler createHandler(
      String manifestType, List<String> manifestContentList, Map<String, Object> overrideProperties);

  public ManifestHandlerChainFactory<S> addHandler(ManifestHandler handler) {
    if (firstHandler == null) {
      firstHandler = handler;
    }
    if (currentHandler != null) {
      currentHandler.setNextHandler(handler);
    }
    currentHandler = handler;
    return this;
  }

  public ManifestHandlerChainFactory<S> addHandler(
      String manifestType, List<String> manifestContentList, Map<String, Object> overrideProperties) {
    ManifestHandler handler = createHandler(manifestType, manifestContentList, overrideProperties);
    return addHandler(handler);
  }

  public ManifestHandlerChainFactory<S> addHandler(
      String manifestType, String manifestContent, Map<String, Object> overrideProperties) {
    return addHandler(manifestType, Arrays.asList(manifestContent), overrideProperties);
  }

  public S executeUpsert() {
    return processChain((handler, chainState) -> (S) handler.upsert(chainState));
  }

  public S executeDelete() {
    return processChain((handler, chainState) -> (S) handler.delete(chainState));
  }

  private S processChain(BiFunction<ManifestHandler, S, S> callHandlerOperation) {
    ManifestHandler handler = firstHandler;
    S chainState = this.initialChainState;
    while (handler != null) {
      chainState = callHandlerOperation.apply(handler, chainState);
      handler = handler.getNextHandler();
    }
    return chainState;
  }
}
