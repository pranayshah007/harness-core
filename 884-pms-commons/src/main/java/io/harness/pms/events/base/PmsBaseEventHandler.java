/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.monitoring.EventMonitoringService;
import io.harness.monitoring.MonitoringInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.events.PmsEventFrameworkConstants;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class PmsBaseEventHandler<T extends Message> implements PmsCommonsBaseEventHandler<T> {
  public static String QUEUE_EVENT_TIME = "%s_queue_time";

  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private EventMonitoringService eventMonitoringService;

  protected PmsGitSyncBranchContextGuard gitSyncContext(T event) {
    return pmsGitSyncHelper.createGitSyncBranchContextGuard(extractAmbiance(event), true);
  }

  @NonNull protected abstract Map<String, String> extraLogProperties(T event);

  protected abstract Ambiance extractAmbiance(T event);

  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, T event) {
    Ambiance ambiance = extractAmbiance(event);
    return ImmutableMap.<String, String>builder()
        .put(PmsEventMonitoringConstants.ACCOUNT_ID, AmbianceUtils.getAccountId(ambiance))
        .put(PmsEventMonitoringConstants.ORG_ID, AmbianceUtils.getOrgIdentifier(ambiance))
        .put(PmsEventMonitoringConstants.PROJECT_ID, AmbianceUtils.getProjectIdentifier(ambiance))
        .put(PmsEventMonitoringConstants.STEP_TYPE, AmbianceUtils.getCurrentStepType(ambiance).getType())
        .put(PmsEventMonitoringConstants.MODULE, metadataMap.get(PmsEventFrameworkConstants.SERVICE_NAME))
        .build();
  }

  protected abstract String getMetricPrefix(T message);

  public void handleEvent(T event, Map<String, String> metadataMap, long messageTimeStamp, long readTs) {
    try (PmsGitSyncBranchContextGuard ignore1 = gitSyncContext(event); AutoLogContext ignore2 = autoLogContext(event);
         PmsMetricContextGuard metricContext =
             new PmsMetricContextGuard(metadataMap, extractMetricContext(metadataMap, event))) {
      log.info("[PMS_MESSAGE_LISTENER] Starting to process {} event ", event.getClass().getSimpleName());
      MonitoringInfo monitoringInfo = MonitoringInfo.builder()
                                          .createdAt(messageTimeStamp)
                                          .readTs(readTs)
                                          .metricPrefix(getMetricPrefix(event))
                                          .metricContext(metricContext)
                                          .accountId(AmbianceUtils.getAccountId(extractAmbiance(event)))
                                          .build();
      eventMonitoringService.sendMetric(QUEUE_EVENT_TIME, monitoringInfo, metadataMap);
      handleEventWithContext(event);
    } catch (Exception ex) {
      try (AutoLogContext autoLogContext = autoLogContext(event)) {
        log.error("Exception occurred while handling {}", event.getClass().getSimpleName(), ex);
      }
      throw ex;
    } finally {
      try (AutoLogContext autoLogContext = autoLogContext(event)) {
        log.info(
            "[PMS_MESSAGE_LISTENER] Event Handler Processing Finished for {} event", event.getClass().getSimpleName());
      }
    }
  }

  protected abstract void handleEventWithContext(T event);

  protected AutoLogContext autoLogContext(T event) {
    Map<String, String> logContext = new HashMap<>();
    logContext.putAll(AmbianceUtils.logContextMap(extractAmbiance(event)));
    logContext.putAll(CollectionUtils.emptyIfNull(extraLogProperties(event)));
    return new AutoLogContext(logContext, OverrideBehavior.OVERRIDE_NESTS);
  }
}
