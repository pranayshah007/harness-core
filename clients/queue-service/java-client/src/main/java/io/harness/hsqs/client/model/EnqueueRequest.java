/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

/**
 * EnqueueRequest Request object for Enqueuing message
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class EnqueueRequest {
  String payload;

  String producerName;

  String subTopic;

  String topic;
}
