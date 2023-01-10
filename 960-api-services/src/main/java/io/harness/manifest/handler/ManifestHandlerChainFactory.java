/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.manifest.request.ManifestRequest;

import java.util.function.BiFunction;

@OwnedBy(CDP)
public abstract class ManifestHandlerChainFactory<S> {
  private final S initialChainState;
  private ManifestHandler currentHandler;
  private ManifestHandler firstHandler;

  public ManifestHandlerChainFactory(S initialChainState) {
    this.initialChainState = initialChainState;
  }

  public abstract ManifestHandler createHandler(String manifestType, ManifestRequest manifestRequest);

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

  public ManifestHandlerChainFactory<S> addHandler(String manifestType, ManifestRequest manifestRequest) {
    ManifestHandler handler = createHandler(manifestType, manifestRequest);
    return addHandler(handler);
  }

  public S executeUpsert() {
    return processChain((handler, chainState) -> (S) handler.upsert(chainState));
  }

  public S executeDelete() {
    return processChain((handler, chainState) -> (S) handler.delete(chainState));
  }

  public S getContent() {
    return processChain((handler, chainState) -> (S) handler.getManifestTypeContent(chainState));
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
