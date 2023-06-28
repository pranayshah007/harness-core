/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static com.google.common.collect.Sets.newHashSet;

import io.harness.delegate.task.TaskParameters;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
public class SpotInstTaskParameters implements TaskParameters {
  private static final Set<SpotInstTaskType> SYNC_TASK_TYPES = newHashSet(SpotInstTaskType.SPOT_INST_LIST_ELASTI_GROUPS,
      SpotInstTaskType.SPOT_INST_GET_ELASTI_GROUP_JSON, SpotInstTaskType.SPOT_INST_LIST_ELASTI_GROUP_INSTANCES);

  private String appId;
  private String accountId;
  private String activityId;
  private String commandName;
  private String workflowExecutionId;
  private Integer timeoutIntervalInMin;
  @NotEmpty private SpotInstTaskType commandType;
  @NotEmpty private String awsRegion;
  private boolean timeoutSupported;

  public enum SpotInstTaskType {
    SPOT_INST_SETUP,
    SPOT_INST_DEPLOY,
    SPOT_INST_SWAP_ROUTES,
    SPOT_INST_LIST_ELASTI_GROUPS,
    SPOT_INST_GET_ELASTI_GROUP_JSON,
    SPOT_INST_LIST_ELASTI_GROUP_INSTANCES,
    SPOT_INST_ALB_SHIFT_SETUP,
    SPOT_INST_ALB_SHIFT_DEPLOY,
    SPOT_INST_ALB_SHIFT_SWAP_ROUTES
  }

  public boolean isSyncTask() {
    return SYNC_TASK_TYPES.contains(commandType);
  }
}
