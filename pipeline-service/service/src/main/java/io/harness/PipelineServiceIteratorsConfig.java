/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.iterator.IteratorExecutionHandler.DynamicIteratorConfig;
import io.harness.mongo.iterator.IteratorConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class PipelineServiceIteratorsConfig {
  @JsonProperty("webhook") IteratorConfig triggerWebhookConfig;
  @JsonProperty("scheduledTrigger") IteratorConfig scheduleTriggerConfig;
  @JsonProperty("timeoutEngine") IteratorConfig timeoutEngineConfig;
  @JsonProperty("timeoutEngineRedisMode") DynamicIteratorConfig timeoutEngineRedisConfig;
  @JsonProperty("barrier") IteratorConfig barrierConfig;
  @JsonProperty("approvalInstance") IteratorConfig approvalInstanceConfig;
  @JsonProperty("resourceRestraint") IteratorConfig resourceRestraintConfig;
  @JsonProperty("interruptMonitor") IteratorConfig interruptMonitorConfig;
}
