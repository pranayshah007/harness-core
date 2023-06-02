/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl.stackdriver;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import java.time.Instant;
import java.time.ZoneId;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EpochToUTCConverter {
  public String fromEpoch(long seconds) {
    Instant time = Instant.ofEpochSecond(seconds);
    return time.atZone(ZoneId.of("UTC")).format(ISO_INSTANT);
  }
}
