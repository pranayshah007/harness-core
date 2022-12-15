/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
public class EventListenersCountConfig {
  int deploymentEventListenerCount;
  int instanceEventListenerCount;
  int deploymentTimeSeriesEventListenerCount;
  int executionInterruptTimeSeriesEventListenerCount;
  int deploymentStepTimeSeriesEventListenerCount;
  int executionEventListenerCount;
  int generalNotifyEventListenerCount;
  int orchestrationNotifyEventListenerCount;
  int notifyConsumerCount;
  int generalConsumerCount;
}
