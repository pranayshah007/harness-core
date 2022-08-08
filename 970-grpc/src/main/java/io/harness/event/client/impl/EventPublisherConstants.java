/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.client.impl;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import net.openhft.chronicle.queue.RollCycles;

@UtilityClass
public class EventPublisherConstants {
  public static final String DEFAULT_QUEUE_FILE_PATH = "eventQueue";
  public static final RollCycles QUEUE_ROLL_CYCLE = RollCycles.MINUTELY;
  public static final long QUEUE_TIMEOUT_MS = Duration.ofSeconds(16).toMillis();
}
