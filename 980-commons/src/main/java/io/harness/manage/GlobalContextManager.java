/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.context.GlobalContextData;
import io.harness.context.MdcGlobalContextData;
import io.harness.kryo.KryoSerializer;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.virtualstack.VirtualStackRequest;

import java.util.Map;
import java.util.concurrent.Callable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class GlobalContextManager {
  private static final ThreadLocal<GlobalContext> contextThreadLocal = new ThreadLocal<>();

  public static class GlobalContextGuard implements AutoCloseable {
    private boolean noop;
    private GlobalContext original;
    AutoLogContext autoLogContext;

    protected GlobalContextGuard(GlobalContext globalContext) {
      original = contextThreadLocal.get();
      noop = globalContext == original;
      if (!noop && globalContext != null) {
        autoLogContext = set(globalContext);
      }
    }

    @Override
    public void close() {
      if (noop) {
        return;
      }

      if (original == null) {
        unset();
      } else {
        contextThreadLocal.set(original);
      }

      if (autoLogContext != null) {
        autoLogContext.close();
      }
    }
  }

  public static GlobalContextGuard ensureGlobalContextGuard() {
    GlobalContext globalContext = contextThreadLocal.get();
    if (globalContext != null) {
      return new GlobalContextGuard(null);
    }
    globalContext = new GlobalContext();
    return new GlobalContextGuard(globalContext);
  }

  public static GlobalContextGuard initGlobalContextGuard(GlobalContext globalContext) {
    if (globalContext == null) {
      globalContext = new GlobalContext();
    }
    return new GlobalContextGuard(globalContext);
  }

  public static GlobalContextGuard initGlobalContextGuard(
      KryoSerializer kryoSerializer, VirtualStackRequest virtualStackRequest) {
    GlobalContext globalContext = null;
    if (virtualStackRequest == null || virtualStackRequest.getGlobalContext() == null) {
      globalContext = new GlobalContext();
    } else {
      globalContext =
          (GlobalContext) kryoSerializer.asInflatedObject(virtualStackRequest.getGlobalContext().toByteArray());
    }
    return new GlobalContextGuard(globalContext);
  }

  public static AutoLogContext set(GlobalContext globalContext) {
    AutoLogContext autoLogContext = null;
    final MdcGlobalContextData globalContextData = globalContext.get(MdcGlobalContextData.MDC_ID);
    if (globalContextData != null) {
      autoLogContext = new AutoLogContext(globalContextData.getMap(), OVERRIDE_ERROR);
    }
    globalContext.unset(MdcGlobalContextData.MDC_ID);
    contextThreadLocal.set(globalContext);
    return autoLogContext;
  }

  public static void unset() {
    contextThreadLocal.remove();
  }

  public static <T extends GlobalContextData> T get(String key) {
    GlobalContext globalContext = contextThreadLocal.get();
    if (globalContext == null) {
      return null;
    }
    return globalContext.get(key);
  }

  static <T> GlobalContextCallableWrapper<T> generateExecutorTask(Callable<T> task) {
    if (task instanceof GlobalContextCallableWrapper) {
      return (GlobalContextCallableWrapper<T>) task;
    }
    return GlobalContextCallableWrapper.<T>builder().task(task).context(obtainGlobalContextCopy()).build();
  }

  static GlobalContextTaskWrapper generateExecutorTask(Runnable task) {
    if (task instanceof GlobalContextTaskWrapper) {
      return (GlobalContextTaskWrapper) task;
    }
    return GlobalContextTaskWrapper.builder().task(task).context(obtainGlobalContextCopy()).build();
  }

  public static void upsertGlobalContextRecord(GlobalContextData data) {
    GlobalContext globalContext = contextThreadLocal.get();
    if (globalContext == null) {
      return;
    }

    globalContext.upsertGlobalContextRecord(data);
  }

  public static boolean isAvailable() {
    return contextThreadLocal.get() != null;
  }

  public static GlobalContext obtainGlobalContext() {
    GlobalContext globalContext = contextThreadLocal.get();
    final Map<String, String> mdc = MDC.getCopyOfContextMap();
    if (isNotEmpty(mdc)) {
      if (globalContext == null) {
        globalContext = new GlobalContext();
      }
      globalContext.upsertGlobalContextRecord(MdcGlobalContextData.builder().map(mdc).build());
    }
    return globalContext;
  }

  public static GlobalContext obtainGlobalContextCopy() {
    GlobalContext globalContext = new GlobalContext(contextThreadLocal.get());
    final Map<String, String> mdc = MDC.getCopyOfContextMap();
    if (isNotEmpty(mdc)) {
      if (globalContext == null) {
        globalContext = new GlobalContext();
      }
      globalContext.upsertGlobalContextRecord(MdcGlobalContextData.builder().map(mdc).build());
    }
    return globalContext;
  }
}
