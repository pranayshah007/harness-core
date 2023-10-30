/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.advise;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.AdviserRegistry;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class NodeAdviseEventHandler extends PmsBaseEventHandler<AdviseEvent> implements NodeAdviseBaseHandler {
  @Inject private AdviserRegistry adviserRegistry;
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;

  @Override
  protected String getMetricPrefix(AdviseEvent message) {
    return "advise_event";
  }

  @NotNull
  @Override
  protected Map<String, String> extraLogProperties(AdviseEvent event) {
    return ImmutableMap.<String, String>builder()
        .put("eventType", NodeExecutionEventType.ADVISE.name())
        .put("notifyId", event.getNotifyId())
        .build();
  }

  @Override
  protected Ambiance extractAmbiance(AdviseEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(AdviseEvent event) {
    try {
      AdviserResponse adviserResponse = handleAdviseEvent(event);

      if (adviserResponse != null) {
        log.debug("Calculated Adviser response is of type {}", adviserResponse.getType());
        sdkNodeExecutionService.handleAdviserResponse(event.getAmbiance(), event.getNotifyId(), adviserResponse);
      } else {
        log.debug("Calculated Adviser response is null. Proceeding with UNKNOWN adviser type.");
        sdkNodeExecutionService.handleAdviserResponse(
            event.getAmbiance(), event.getNotifyId(), AdviserResponse.newBuilder().setType(AdviseType.UNKNOWN).build());
      }
    } catch (Exception ex) {
      log.error("Error while advising execution", ex);
      if (EmptyPredicate.isEmpty(event.getNotifyId())) {
        log.debug("NotifyId is empty for nodeExecutionId {} and planExecutionId {}. Nothing will happen.",
            AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance()), event.getAmbiance().getPlanExecutionId());
      } else {
        sdkNodeExecutionService.handleEventError(NodeExecutionEventType.ADVISE, event.getAmbiance(),
            event.getNotifyId(), NodeExecutionUtils.constructFailureInfo(ex));
      }
    }
  }

  @Override
  public AdviserRegistry getAdviserRegistry() {
    return adviserRegistry;
  }
}
