/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.customhealthlog;

import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomHealthLogInfo {
  String queryName;
  String body;
  String urlPath;
  String logMessageJsonPath;
  String serviceInstanceJsonPath;
  String timestampJsonPath;
  CustomHealthMethod method;
  TimestampInfo startTimeInfo;
  TimestampInfo endTimeInfo;
}