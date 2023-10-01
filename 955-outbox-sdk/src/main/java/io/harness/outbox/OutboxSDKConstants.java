/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SortOrder.OrderType.ASC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.outbox.OutboxEvent.OutboxEventKeys;
import io.harness.outbox.filter.OutboxEventFilter;

import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class OutboxSDKConstants {
  public static final List<SortOrder> DEFAULT_CREATED_AT_ASC_SORT_ORDER =
      Collections.singletonList(SortOrder.Builder.aSortOrder().withField(OutboxEventKeys.createdAt, ASC).build());

  public static final int DEFAULT_MAX_ATTEMPTS = 7;

  public static final int DEFAULT_MAX_EVENTS_POLLED = 100;

  public static final int DEFAULT_UNBLOCK_RETRY_INTERVAL_IN_MINUTES = 10;

  public static final OutboxPollConfiguration DEFAULT_OUTBOX_POLL_CONFIGURATION =
      OutboxPollConfiguration.builder()
          .maximumRetryAttemptsForAnEvent(DEFAULT_MAX_ATTEMPTS)
          .initialDelayInSeconds(5)
          .pollingIntervalInSeconds(5)
          .build();

  public static final OutboxEventFilter DEFAULT_OUTBOX_EVENT_FILTER =
      OutboxEventFilter.builder().maximumEventsPolled(DEFAULT_MAX_EVENTS_POLLED).build();

  public static final String OUTBOX_QUEUE_SIZE_METRIC_NAME = "outbox_queue_size";

  public static final String OUTBOX_BLOCKED_QUEUE_SIZE_METRIC_NAME = "outbox_blocked_queue_size";

  public static final String OUTBOX_EVENT_PROCESSING_TIME_METRIC_NAME = "outbox_event_processing_time";

  public static final String ALL_EVENT_TYPES = "AllEventTypes";
}
