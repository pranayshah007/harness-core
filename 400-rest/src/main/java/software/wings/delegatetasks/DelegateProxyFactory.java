/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;

import software.wings.beans.SyncTaskContext;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Proxy;

@Singleton
@TargetModule(HarnessModule._910_DELEGATE_SERVICE_DRIVER)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class DelegateProxyFactory {
  @Inject private DelegateService delegateService;
  @Inject private TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  public <T> T get(Class<T> klass, SyncTaskContext syncTaskContext) {
    return (T) Proxy.newProxyInstance(klass.getClassLoader(), new Class[] {klass},
        new DelegateInvocationHandler(syncTaskContext, delegateService, taskSetupAbstractionHelper));
  }

  public <T> T getV2(Class<T> klass, SyncTaskContext syncTaskContext) {
    return (T) Proxy.newProxyInstance(klass.getClassLoader(), new Class[] {klass},
        new DelegateInvocationHandler(syncTaskContext, delegateService, taskSetupAbstractionHelper));
  }
}
