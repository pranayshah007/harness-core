/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
@Value
@Builder
public class HostRecordDTO {
  String accountId;
  String verificationTaskId;
  Set<String> hosts;
  Instant startTime;
  Instant endTime;
}
