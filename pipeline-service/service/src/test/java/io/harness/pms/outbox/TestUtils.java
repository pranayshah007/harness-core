/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;

import io.harness.context.GlobalContext;
import io.harness.event.Event;
import io.harness.outbox.OutboxEvent;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;

public class TestUtils {
  private static ObjectMapper objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

  public static OutboxEvent createOutboxEvent(Event event, String pipelineOutboxEvent) throws Exception {
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(event);
    return OutboxEvent.builder()
        .resource(event.getResource())
        .resourceScope(event.getResourceScope())
        .eventType(pipelineOutboxEvent)
        .blocked(false)
        .globalContext(globalContext)
        .createdAt(Long.valueOf(randomNumeric(6)))
        .eventData(eventData)
        .id(randomAlphabetic(10))
        .build();
  }
}
