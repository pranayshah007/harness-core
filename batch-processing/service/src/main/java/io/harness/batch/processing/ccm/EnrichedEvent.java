/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import io.harness.ccm.commons.beans.HarnessServiceInfo;

import javax.annotation.Nullable;
import lombok.Value;

@Value
public class EnrichedEvent<T> {
  String accountId;
  long occurredAt;

  T event;
  @Nullable HarnessServiceInfo harnessServiceInfo;
}
