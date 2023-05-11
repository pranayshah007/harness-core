/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.bean;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
public class CGMonitoredServiceEntity implements NGMigrationEntity {
  private static final String WORKFLOW_ID_PREFIX = "workflow_";
  private static final String STEP_DIVIDER = "_step_";

  Workflow workflow;
  GraphNode stepNode;

  public static String getWorkflowIdFromMonitoredServiceId(String monitoredServiceId) {
    int endOfWorkflowId = monitoredServiceId.indexOf(STEP_DIVIDER);
    int startOfWorkflowId = WORKFLOW_ID_PREFIX.length();
    return StringUtils.substring(monitoredServiceId, startOfWorkflowId, endOfWorkflowId);
  }

  public static String getStepIdFromMonitoredServiceId(String monitoredServiceId) {
    int startOfStepId = monitoredServiceId.indexOf(STEP_DIVIDER) + STEP_DIVIDER.length();
    return StringUtils.substring(monitoredServiceId, startOfStepId);
  }

  public static String getMonitoredServiceId(String workflowId, String stepId) {
    return WORKFLOW_ID_PREFIX + workflowId + STEP_DIVIDER + stepId;
  }

  public String getId() {
    return getMonitoredServiceId(workflow.getUuid(), stepNode.getId());
  }

  public String getName() {
    return getMonitoredServiceId(workflow.getName(), stepNode.getName());
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
