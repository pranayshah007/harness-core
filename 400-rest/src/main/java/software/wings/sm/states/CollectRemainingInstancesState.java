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
import software.wings.beans.Base;
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
import java.util.stream.Collectors;
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

    if (!isAutoScaleGroupSet(appId, infraMappingId)) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.SKIPPED).build();
    }

    String serviceId = ((PhaseElement) context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
                           .getServiceElement()
                           .getUuid();

    List<ServiceInstance> activeInstances =
        getAllServiceInstancesInASG(appId, infraMappingId, context)
            .stream()
            .filter(serviceInstance -> serviceInstance.getServiceId().equals(serviceId))
            .collect(toList());

    // all active instance ids
    Set<String> activeInstanceIds = activeInstances.stream().map(Base::getUuid).collect(Collectors.toSet());

    List<SweepingOutputInstance> sweepingOutputInstances = sweepingOutputService.findManyWithNamePrefix(
        context.prepareSweepingOutputInquiryBuilder().name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS).build(),
        SweepingOutputInstance.Scope.WORKFLOW);

    // instances that are already rolled back or will be rolled back in other phases
    Set<String> instancesDeployedInOtherPhases = new HashSet<>();
    // instances that were initially deployed in this phase
    Set<String> instancesForRollback = new HashSet<>();
    for (SweepingOutputInstance sweepingOutputInstance : sweepingOutputInstances) {
      ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) sweepingOutputInstance.getValue();
      if (sweepingOutputInstance.getName().equals(
              ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + getPhaseNameForRollback(context))) {
        instancesForRollback.addAll(serviceInstanceIdsParam.getInstanceIds());
      } else {
        instancesDeployedInOtherPhases.addAll(serviceInstanceIdsParam.getInstanceIds());
      }
    }

    List<String> newInstances =
        activeInstanceIds.stream()
            .filter(serviceInstance -> !instancesDeployedInOtherPhases.contains(serviceInstance))
            .filter(serviceInstance -> !instancesForRollback.contains(serviceInstance))
            .collect(toList());

    // get count of initially deployed instances before updating a list
    int initiallyDeployedInstancesCount = instancesForRollback.size();

    // remove inactive instances from list
    instancesForRollback.removeIf(instance -> !activeInstanceIds.contains(instance));

    // count how many we want to rollback
    int countRollback =
        calculateRollbackCount(sweepingOutputInstances, getPhaseNameForRollback(context), activeInstanceIds.size());

    int missingInstancesCount = countRollback - instancesForRollback.size();
    if (missingInstancesCount > 0) {
      instancesForRollback.addAll(
          newInstances.size() <= missingInstancesCount ? newInstances : newInstances.subList(0, missingInstancesCount));
    }

    // if it's last rollback phase add all remaining
    if (context.isLastPhase(true)) {
      instancesForRollback.addAll(newInstances);
    }

    List<ServiceInstance> serviceInstances =
        activeInstances.stream()
            .filter(serviceInstance -> instancesForRollback.contains(serviceInstance.getUuid()))
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
            .withInitiallyDeployedInstancesCount(initiallyDeployedInstancesCount)
            .build();

    updateServiceInstancesInSweepingOutput(context, serviceIdParamElement);

    return ExecutionResponse.builder()
        .contextElement(serviceIdParamElement)
        .notifyElement(serviceIdParamElement)
        .stateExecutionData(selectedNodeExecutionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .build();
  }

  private int calculateRollbackCount(
      List<SweepingOutputInstance> sweepingOutputInstances, String phaseName, int allActiveInstancesCount) {
    double allInitiallyDeployedInstancesCount = 0;
    double numberOfInstancesDeployedInCurrentPhase = 0;
    for (SweepingOutputInstance sweepingOutputInstance : sweepingOutputInstances) {
      ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) sweepingOutputInstance.getValue();
      allInitiallyDeployedInstancesCount += serviceInstanceIdsParam.getInitiallyDeployedInstancesCount() == 0
          ? serviceInstanceIdsParam.getInstanceIds().size()
          : serviceInstanceIdsParam.getInitiallyDeployedInstancesCount();

      if (sweepingOutputInstance.getName().equals(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + phaseName)) {
        numberOfInstancesDeployedInCurrentPhase = serviceInstanceIdsParam.getInstanceIds().size();
      }
    }

    if (allInitiallyDeployedInstancesCount == 0 || numberOfInstancesDeployedInCurrentPhase == 0) {
      return 0;
    }

    return (int) Math.round(
        allActiveInstancesCount * numberOfInstancesDeployedInCurrentPhase / allInitiallyDeployedInstancesCount);
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
