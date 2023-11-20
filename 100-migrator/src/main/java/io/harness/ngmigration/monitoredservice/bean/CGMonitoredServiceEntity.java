/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.bean;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Data
@Builder
public class CGMonitoredServiceEntity implements NGMigrationEntity {
  private static final String WORKFLOW_ID_PREFIX = "workflow_";
  private static final String STEP_DIVIDER = "_step_";
  private static final String PHASE_STEP_DIVIDER = "_phasestep_";
  private static final String PHASE_DIVIDER = "_phase_";

  Workflow workflow;
  GraphNode stepNode;
  PhaseStep phaseStep;
  WorkflowPhase phase;

  public static String getWorkflowIdFromMonitoredServiceId(String monitoredServiceId) {
    int endOfWorkflowId = monitoredServiceId.indexOf(PHASE_DIVIDER);
    int startOfWorkflowId = WORKFLOW_ID_PREFIX.length();
    return StringUtils.substring(monitoredServiceId, startOfWorkflowId, endOfWorkflowId);
  }

  public static String getStepIdFromMonitoredServiceId(String monitoredServiceId) {
    int startOfStepId = monitoredServiceId.indexOf(STEP_DIVIDER) + STEP_DIVIDER.length();
    return StringUtils.substring(monitoredServiceId, startOfStepId);
  }

  public static String getMonitoredServiceId(
      String workflowId, WorkflowPhase phase, PhaseStep phaseStep, String stepId) {
    return String.format("%s%s%s%s%s%s%s%s", WORKFLOW_ID_PREFIX, workflowId, PHASE_DIVIDER,
        phase == null ? "" : phase.getName(), PHASE_STEP_DIVIDER, phaseStep == null ? "" : phaseStep.getName(),
        STEP_DIVIDER, stepId);
  }

  public String getId() {
    return getMonitoredServiceId(workflow.getUuid(), phase, phaseStep, stepNode.getId());
  }

  public String getName() {
    return getMonitoredServiceId(workflow.getName(), phase, phaseStep, stepNode.getName());
  }

  @Override
  public String getMigrationEntityName() {
    return "Monitored Service";
  }

  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .accountId(workflow.getAccountId())
        .appId(workflow.getAppId())
        .id(getId())
        .name(getName())
        .type(NGMigrationEntityType.CONFIG_FILE)
        .build();
  }
}
