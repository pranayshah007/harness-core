/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

public enum EcsCommandTypeNG {
  ECS_ROLLING_DEPLOY,
  ECS_PREPARE_ROLLBACK_DATA,
  ECS_ROLLING_ROLLBACK,
  ECS_CANARY_DEPLOY,
  ECS_CANARY_DELETE,
  ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA,
  ECS_BLUE_GREEN_CREATE_SERVICE,
  ECS_BLUE_GREEN_SWAP_TARGET_GROUPS,
  ECS_BLUE_GREEN_ROLLBACK,
  ECS_RUN_TASK,
  ECS_RUN_TASK_ARN,
  ECS_TASK_ARN_ROLLING_DEPLOY,
  ECS_TASK_ARN_CANARY_DEPLOY,
  ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE,
  ECS_BASIC_PREPARE_ROLLBACK,
  ECS_BASIC_ROLLBACK,
  ECS_SERVICE_SETUP,
  ECS_UPGRADE_CONTAINER
}
