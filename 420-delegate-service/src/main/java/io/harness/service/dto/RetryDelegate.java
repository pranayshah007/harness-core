/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.dto;

import io.harness.beans.DelegateTask;

import dev.morphia.query.Query;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RetryDelegate {
  String delegateId;
  Query<DelegateTask> taskQuery;
  DelegateTask delegateTask;
  boolean retryPossible;
}
