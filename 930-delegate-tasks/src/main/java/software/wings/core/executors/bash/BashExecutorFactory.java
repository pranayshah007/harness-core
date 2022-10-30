/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.executors.bash;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.DummyLogCallbackImpl;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BashExecutorFactory {
  public ScriptProcessExecutor getExecutor(ShellExecutorConfig config) {
    return new ScriptProcessExecutor(new DummyLogCallbackImpl(), true, config);
  }
}
