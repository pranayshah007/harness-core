/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
@Singleton
public class CCMSettingsCRUDStreamListener implements MessageListener {
  @Override
  public boolean handleMessage(Message message) {
    log.info("CCMSettingsCRUDStreamListener Message: {}", message);
    return true;
  }
}
