/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationEventsFrameworkConstants {
  public static final String SDK_RESPONSE_EVENT_CONSUMER = "SDK_RESPONSE_EVENT_CONSUMER";

  public static final String PARTIAL_PLAN_EVENT_CONSUMER = "PARTIAL_PLAN_EVENT_RESPONSE_CONSUMER";

  public static final String INITIATE_NODE_EVENT_CONSUMER = "INITIATE_NODE_EVENT_CONSUMER";

  public static final String INITIATE_NODE_EVENT_PRODUCER = "TRIGGER_NODE_EVENT_PRODUCER";
}
