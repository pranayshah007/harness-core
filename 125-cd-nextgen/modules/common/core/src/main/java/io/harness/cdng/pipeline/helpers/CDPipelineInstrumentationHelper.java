/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ACCOUNT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ACCOUNT_NAME;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT_EVENT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ORG_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_PIPELINE_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_PROJECT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_ACCOUNT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_ACCOUNT_NAME;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_COUNT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_COUNT_EVENT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_ORG_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_PIPELINE_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_PROJECT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ACCOUNT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ACCOUNT_NAME;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EVENT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EVENT_PIPELINE_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EXECUTION_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ORG_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_PROJECT_ID;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_SERVICE_ID;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.service.instance.InstanceService;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CDPipelineInstrumentationHelper {
  @Inject InstanceService instanceService;
  @Inject TelemetryReporter telemetryReporter;

  public long getCountOfServiceInstancesDeployedInInterval(String accountId, long startTS, long endTS) {
    return getCountOfServiceInstancesDeployedInInterval(accountId, null, null, startTS, endTS);
  }

  public long getCountOfServiceInstancesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS) {
    if (EmptyPredicate.isEmpty(orgId) && EmptyPredicate.isEmpty(projectId)) {
      return instanceService.countServiceInstancesDeployedInInterval(accountId, startTS, endTS);
    }
    return instanceService.countServiceInstancesDeployedInInterval(accountId, orgId, projectId, startTS, endTS);
  }

  public long getCountOfDistinctActiveServicesDeployedInInterval(String accountId, long startTS, long endTS) {
    return instanceService.countDistinctActiveServicesDeployedInInterval(accountId, startTS, endTS);
  }

  public long getCountOfDistinctActiveServicesDeployedInInterval(
      String accountId, String orgId, String projectId, long startTS, long endTS) {
    return instanceService.countDistinctActiveServicesDeployedInInterval(accountId, orgId, projectId, startTS, endTS);
  }

  public void sendCountOfDistinctActiveServicesEvent(String pipelineId, String identity, String accountId,
      String accountName, String orgId, String projectId, long count) {
    try {
      HashMap<String, Object> activeServicesCountPropMap = new HashMap<>();
      activeServicesCountPropMap.put(ACTIVE_SERVICES_COUNT, count);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ACCOUNT_ID, accountId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ACCOUNT_NAME, accountName);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_PROJECT_ID, projectId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ORG_ID, orgId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_PIPELINE_ID, pipelineId);
      telemetryReporter.sendTrackEvent(ACTIVE_SERVICES_COUNT_EVENT, identity, accountId, activeServicesCountPropMap,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
          io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());

    } catch (Exception e) {
      log.error("Failed to send count of active services event.", e);
    }
  }

  public void sendCountOfServiceInstancesEvent(String pipelineId, String identity, String accountId, String accountName,
      String orgId, String projectId, long countOfServiceInstances) {
    try {
      HashMap<String, Object> serviceInstancesPropMap = new HashMap<>();
      serviceInstancesPropMap.put(SERVICE_INSTANCES_COUNT, countOfServiceInstances);
      serviceInstancesPropMap.put(SERVICE_INSTANCES_ACCOUNT_ID, accountId);
      serviceInstancesPropMap.put(SERVICE_INSTANCES_ACCOUNT_NAME, accountName);
      serviceInstancesPropMap.put(SERVICE_INSTANCES_PROJECT_ID, projectId);
      serviceInstancesPropMap.put(SERVICE_INSTANCES_ORG_ID, orgId);
      serviceInstancesPropMap.put(SERVICE_INSTANCES_PIPELINE_ID, pipelineId);
      telemetryReporter.sendTrackEvent(SERVICE_INSTANCES_COUNT_EVENT, identity, accountId, serviceInstancesPropMap,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
          io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());
    } catch (Exception e) {
      log.error("Failed to send count of service instances event.", e);
    }
  }

  public void sendServiceUsedEventsForPipelineExecution(String eventPipelineId, String identity, String accountId,
      String accountName, String orgId, String projectId, String planExecutionId, OrchestrationEvent event) {
    Set<String> serviceIds = getServiceIdsForPipelineExecution(event);

    serviceIds.forEach(serviceId
        -> sendServiceUsedInPipelineExecutionEvent(
            eventPipelineId, identity, accountId, accountName, orgId, projectId, planExecutionId, serviceId));
  }

  private void sendServiceUsedInPipelineExecutionEvent(String eventPipelineId, String identity, String accountId,
      String accountName, String orgId, String projectId, String planExecutionId, String serviceId) {
    try {
      HashMap<String, Object> serviceUsedPropMap = new HashMap<>();
      serviceUsedPropMap.put(SERVICE_USED_EXECUTION_ID, planExecutionId);
      serviceUsedPropMap.put(SERVICE_USED_SERVICE_ID, serviceId);
      serviceUsedPropMap.put(SERVICE_USED_ACCOUNT_ID, accountId);
      serviceUsedPropMap.put(SERVICE_USED_ACCOUNT_NAME, accountName);
      serviceUsedPropMap.put(SERVICE_USED_PROJECT_ID, projectId);
      serviceUsedPropMap.put(SERVICE_USED_ORG_ID, orgId);
      serviceUsedPropMap.put(SERVICE_USED_EVENT_PIPELINE_ID, eventPipelineId);
      telemetryReporter.sendTrackEvent(SERVICE_USED_EVENT, identity, accountId, serviceUsedPropMap,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
          io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());
    } catch (Exception e) {
      log.error("Failed to send service used event.", e);
    }
  }

  private Set<String> getServiceIdsForPipelineExecution(OrchestrationEvent event) {
    Set<String> services = new HashSet<>();

    try {
      CDPipelineModuleInfo cdPipelineModuleInfo = (CDPipelineModuleInfo) event.getModuleInfo();

      if (cdPipelineModuleInfo != null) {
        List<String> serviceIds = cdPipelineModuleInfo.getServiceIdentifiers();

        if (EmptyPredicate.isNotEmpty(serviceIds)) {
          serviceIds.forEach(s -> services.add(s));
        }
      }
    } catch (Exception e) {
      log.error("Problem obtaining service ids from orchestration event.", e);
    }
    return services;
  }
}
