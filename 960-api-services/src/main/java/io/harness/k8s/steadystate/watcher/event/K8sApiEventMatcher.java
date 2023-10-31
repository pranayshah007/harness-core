/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.LogCallback;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import java.time.OffsetDateTime;

@Singleton
@OwnedBy(HarnessTeam.CDP)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_K8S)
public class K8sApiEventMatcher {
  private static final String K8S_WARNING_EVENT_TYPE = "WARNING";
  private static final String K8S_EVENT_LOG_PREFIX = "Event";
  public boolean isEventEmittedPostDeployment(CoreV1Event event, OffsetDateTime startTime) {
    if (event.getEventTime() != null) {
      return event.getEventTime().isAfter(startTime);
    }

    if (event.getFirstTimestamp() != null && event.getLastTimestamp() != null) {
      return event.getFirstTimestamp().isBefore(startTime) && event.getLastTimestamp().isAfter(startTime);
    }

    if (event.getFirstTimestamp() == null && event.getLastTimestamp() != null) {
      return event.getLastTimestamp().isAfter(startTime);
    }

    if (event.getFirstTimestamp() != null && event.getLastTimestamp() == null) {
      return event.getFirstTimestamp().isBefore(startTime);
    }

    return false;
  }

  public void logEvents(CoreV1Event event, LogCallback logCallback, String infoFormat, String warningFormat) {
    V1ObjectReference ref = event.getInvolvedObject();
    if (K8S_WARNING_EVENT_TYPE.equalsIgnoreCase(event.getType())) {
      logCallback.saveExecutionLog(format(warningFormat, K8S_EVENT_LOG_PREFIX, event.getMessage()));
    } else {
      logCallback.saveExecutionLog(format(infoFormat, K8S_EVENT_LOG_PREFIX, ref.getName(), event.getMessage()));
    }
  }
}
