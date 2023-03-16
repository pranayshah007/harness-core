/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.utility;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.eventsframework.consumer.Message;

import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class EventListenerLogger {
  private static final String ACCOUNT_ID = "accountId";

  public void logForEventReceived(Message message) {
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    log.info("Received event. Entity type : {}, Action: {}, Account: {}", metadataMap.get(ENTITY_TYPE),
        metadataMap.get(ACTION), metadataMap.get(ACCOUNT_ID));
  }
}
