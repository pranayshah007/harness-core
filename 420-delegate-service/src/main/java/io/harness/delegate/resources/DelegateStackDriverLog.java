/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DelegateStackDriverLog {
  String ISOTime;
  String severity;

  // data from payload
  String taskId;
  String logger;
  String thread;
  String message;
  String exception;

  // data from labels
  String accountId;
  String delegateId;
  String app;
  String source;
  String processId;
  String version;
  String managerHost;
}
