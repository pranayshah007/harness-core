/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logging.AutoLogContext;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.persistence.PersistenceWrapper;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyEventListenerHelper {
  @Inject private Injector injector;
  @Inject private PersistenceWrapper persistenceWrapper;
  @Inject private WaitInstanceService waitInstanceService;

  public void onMessage(String waitInstanceId) {
    try (AutoLogContext ignore = new WaitInstanceLogContext(waitInstanceId, OVERRIDE_ERROR)) {
      final long now = System.currentTimeMillis();
      WaitInstance waitInstance = waitInstanceService.fetchForProcessingWaitInstance(waitInstanceId, now);

      if (waitInstance == null) {
        log.error("WaitInstance was already handled!");
        return;
      }

      ProcessedMessageResponse response = persistenceWrapper.processMessage(waitInstance);

      NotifyCallback callback = waitInstance.getCallback();
      if (callback != null) {
        injector.injectMembers(callback);
        processCallback(callback, response.getResponseDataMap(), response.isError());
      }

      persistenceWrapper.deleteWaitInstance(waitInstance);

      waitInstanceService.checkProcessingTime(now);
    }
  }

  private void processCallback(NotifyCallback notifyCallback, Map<String, ResponseData> responseMap, boolean isError) {
    try {
      if (notifyCallback instanceof OldNotifyCallback) {
        if (isError) {
          ((OldNotifyCallback) notifyCallback).notifyError(responseMap);
        } else {
          ((OldNotifyCallback) notifyCallback).notify(responseMap);
        }
      } else if (notifyCallback instanceof PushThroughNotifyCallback) {
        ((PushThroughNotifyCallback) notifyCallback).push(responseMap);
      } else if (notifyCallback instanceof NotifyCallbackWithErrorHandling) {
        ((NotifyCallbackWithErrorHandling) notifyCallback).notify(prepareResponseWithError(responseMap));
      } else {
        throw new UnsupportedOperationException(
            "No handling present for notify callback : " + notifyCallback.toString());
      }
      log.info("WaitInstance callback finished");
    } catch (Exception exception) {
      log.error("WaitInstance callback failed", exception);
    }
  }

  private Map<String, Supplier<ResponseData>> prepareResponseWithError(Map<String, ResponseData> responseMap) {
    Map<String, Supplier<ResponseData>> finalResponseMap = new HashMap<>();
    responseMap.forEach((k, v) -> {
      final Supplier<ResponseData> responseDataSupplier = () -> {
        if (v instanceof ErrorResponseData) {
          if (((ErrorResponseData) v).getException() == null) {
            log.info("Exception is null for responseMap {}", v, new Exception());
          }
          throw((ErrorResponseData) v).getException();
        } else {
          return v;
        }
      };
      finalResponseMap.put(k, responseDataSupplier);
    });

    return finalResponseMap;
  }
}
