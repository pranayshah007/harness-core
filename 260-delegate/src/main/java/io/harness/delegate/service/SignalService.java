/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class SignalService {
  private AtomicReference<State> state = new AtomicReference<>(State.RUNNING);

  @Inject private DelegateAgentService delegateService;

  void pause() {
    if (state.compareAndSet(State.RUNNING, State.PAUSE)) {
      log.info("[Old] Setting state to pause from running");
      delegateService.pause();
      log.info("[Old] Delegate paused");
    }
  }

  void stop() {
    state.set(State.STOP);
    log.info("[Old] Setting state to stopped");
    delegateService.stop();
    log.info("[Old] Delegate stopped");
  }

  public enum State { RUNNING, PAUSE, STOP }
}
