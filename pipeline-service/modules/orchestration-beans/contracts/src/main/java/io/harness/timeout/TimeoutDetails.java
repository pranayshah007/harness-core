/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timeout;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@TypeAlias("timeoutDetails")
public class TimeoutDetails {
  TimeoutInstance timeoutInstance;
  long expiredAt;

  public TimeoutDetails(TimeoutInstance timeoutInstance) {
    this.timeoutInstance = timeoutInstance;
    this.expiredAt = System.currentTimeMillis();
  }
}
