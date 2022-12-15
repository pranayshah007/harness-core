/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import io.harness.ccm.commons.beans.InstanceType;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceEvent {
  String accountId;
  String cloudProviderId;
  String clusterId;
  String instanceId;
  String instanceName;
  Instant timestamp;
  EventType type;
  InstanceType instanceType;
  public enum EventType { STOP, START, UNKNOWN }
}
