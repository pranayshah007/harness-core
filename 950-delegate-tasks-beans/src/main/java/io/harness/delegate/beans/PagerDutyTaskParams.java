/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import com.github.dikhan.pagerduty.client.events.domain.LinkContext;
import com.github.dikhan.pagerduty.client.events.domain.Payload;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class PagerDutyTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  List<String> pagerDutyKeys;
  Payload payload;
  List<LinkContext> links;
  String notificationId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.emptyList();
  }
}
