/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.waiter.NotifyEventListenerHelper;
import io.harness.waiter.notify.NotifyEventProto;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyEventHandler implements PmsCommonsBaseEventHandler<NotifyEventProto> {
  @Inject NotifyEventListenerHelper notifyEventListenerHelper;

  @Override
  public void handleEvent(NotifyEventProto event, Map<String, String> metadataMap, long messageTimeStamp, long readTs) {
    notifyEventListenerHelper.onMessage(event.getWaitInstanceId());
  }
}
