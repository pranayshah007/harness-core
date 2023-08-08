/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.notification.PipelineEventType;
import io.harness.notification.TriggerExecutionInfo;

import lombok.Builder;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Getter
@Builder
public class WebhookNotificationEvent {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  String planExecutionId;
  String stageIdentifier;
  String stepIdentifier;
  String executionUrl;
  String pipelineUrl;
  PipelineEventType eventType;
  String nodeStatus;
  TriggerExecutionInfo triggeredBy;
  ModuleInfo moduleInfo;
  String startTime;
  Long startTs;
  String endTime;
  Long endTs;
}
