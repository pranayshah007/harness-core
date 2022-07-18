/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static software.wings.api.ServiceInstanceIdsParam.ServiceInstanceIdsParamBuilder.aServiceInstanceIdsParam;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;

import software.wings.api.PhaseElement;
import software.wings.api.SelectedNodeExecutionData;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.ServiceInstance;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CollectRemainingInstancesState extends State {
  @Inject @Transient private InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private SweepingOutputService sweepingOutputService;

  public CollectRemainingInstancesState(String name) {
    super(name, StateType.COLLECT_REMAINING_INSTANCES.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String appId = requireNonNull(context.getApp()).getUuid();
    String infraMappingId = context.fetchInfraMappingId();

    if (!context.isLastPhase(true) || !isAutoScaleGroupSet(appId, infraMappingId)) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.SKIPPED).build();
    }

    String serviceId = ((PhaseElement) context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
                           .getServiceElement()
                           .getUuid();
    Set<String> alreadyRolledBackInstances = getRolledBackInstances(context);

    List<ServiceInstance> serviceInstances =
        getAllServiceInstancesInASG(appId, infraMappingId, context)
            .stream()
            .filter(serviceInstance -> serviceInstance.getServiceId().equals(serviceId))
            .filter(serviceInstance -> !alreadyRolledBackInstances.contains(serviceInstance.getUuid()))
            .collect(toList());

    SelectedNodeExecutionData selectedNodeExecutionData = new SelectedNodeExecutionData();
    selectedNodeExecutionData.setServiceInstanceList(serviceInstances.stream()
                                                         .map(serviceInstance
                                                             -> aServiceInstance()
                                                                    .withUuid(serviceInstance.getUuid())
                                                                    .withHostId(serviceInstance.getHostId())
                                                                    .withHostName(serviceInstance.getHostName())
                                                                    .withPublicDns(serviceInstance.getPublicDns())
                                                                    .build())
                                                         .collect(toList()));

    ServiceInstanceIdsParam serviceIdParamElement =
        aServiceInstanceIdsParam()
            .withInstanceIds(serviceInstances.stream().map(ServiceInstance::getUuid).collect(toList()))
            .withServiceId(serviceId)
            .build();

    updateServiceInstancesInSweepingOutput(context, serviceIdParamElement);

    return ExecutionResponse.builder()
        .contextElement(serviceIdParamElement)
        .notifyElement(serviceIdParamElement)
        .stateExecutionData(selectedNodeExecutionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .build();
  }

  private Set<String> getRolledBackInstances(ExecutionContext context) {
    Set<String> alreadyRolledBackInstances = new HashSet<>();
    String phaseNameForRollback = getPhaseNameForRollback(context);
    if (phaseNameForRollback == null) {
      return alreadyRolledBackInstances;
    }

    sweepingOutputService
        .findManyWithNamePrefix(context.prepareSweepingOutputInquiryBuilder()
                                    .name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS)
                                    .build(),
            SweepingOutputInstance.Scope.WORKFLOW)
        .stream()
        .filter(sweepingOutputInstance
            -> !sweepingOutputInstance.getName().equals(
                ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + phaseNameForRollback))
        .forEach(sweepingOutputInstance
            -> alreadyRolledBackInstances.addAll(
                ((ServiceInstanceIdsParam) sweepingOutputInstance.getValue()).getInstanceIds()));

    return alreadyRolledBackInstances;
  }

  private void updateServiceInstancesInSweepingOutput(
      ExecutionContext context, ServiceInstanceIdsParam serviceIdParamElement) {
    String phaseName = getPhaseNameForRollback(context);
    if (phaseName == null) {
      return;
    }

    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder()
                                       .name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + phaseName)
                                       .build());
    sweepingOutputService.deleteById(sweepingOutputInstance.getAppId(), sweepingOutputInstance.getUuid());

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + phaseName)
                                   .value(serviceIdParamElement)
                                   .build());
  }

  @Override
  public Map<String, String> validateFields() {
    return new HashMap<>();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private List<ServiceInstance> getAllServiceInstancesInASG(
      String appId, String infraMappingId, ExecutionContext context) {
    int totalAvailableInstances =
        infrastructureMappingService.listHostDisplayNames(appId, infraMappingId, context.getWorkflowExecutionId())
            .size();

    return infrastructureMappingService.selectServiceInstances(appId, infraMappingId, context.getWorkflowExecutionId(),
        aServiceInstanceSelectionParams().withCount(totalAvailableInstances).withSelectSpecificHosts(false).build());
  }

  private String getPhaseNameForRollback(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    if (phaseElement == null || phaseElement.getPhaseNameForRollback() == null) {
      return null;
    }
    return phaseElement.getPhaseNameForRollback();
  }

  private boolean isAutoScaleGroupSet(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;
      return awsInfrastructureMapping.isProvisionInstances();
    }
    return false;
  }
}
