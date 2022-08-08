/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.sidekick.RetryChangeSourceHandleDeleteSideKickData;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.impl.ChangeSourceUpdateHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RetryChangeSourceHandleDeleteSideKickExecutor
    implements SideKickExecutor<RetryChangeSourceHandleDeleteSideKickData> {
  @Inject private Map<ChangeSourceType, ChangeSourceUpdateHandler> changeSourceUpdateHandlerMap;
  @Inject private Clock clock;

  @Override
  public void execute(RetryChangeSourceHandleDeleteSideKickData sideKickInfo) {
    log.info("SidekickInfo {}", sideKickInfo);
    if (changeSourceUpdateHandlerMap.containsKey(sideKickInfo.getChangeSource().getType())) {
      changeSourceUpdateHandlerMap.get(sideKickInfo.getChangeSource().getType())
          .handleDelete(sideKickInfo.getChangeSource());
    }
  }

  @Override
  public RetryData shouldRetry(int lastRetryCount) {
    if (lastRetryCount < 5) {
      return RetryData.builder().shouldRetry(true).nextRetryTime(clock.instant().plusSeconds(300)).build();
    }
    return RetryData.builder().shouldRetry(false).build();
  }
}
