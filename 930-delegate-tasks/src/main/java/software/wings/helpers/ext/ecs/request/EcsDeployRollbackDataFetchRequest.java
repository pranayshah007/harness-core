/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecs.request;

import static software.wings.helpers.ext.ecs.request.EcsCommandRequest.EcsCommandType.DEPLOY_ROLLBACK_DATA_FETCH;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.EcsResizeParams;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class EcsDeployRollbackDataFetchRequest extends EcsCommandRequest {
  private EcsResizeParams ecsResizeParams;
  private boolean blueGreen;

  @Builder
  public EcsDeployRollbackDataFetchRequest(String accountId, String appId, String commandName, String activityId,
      String region, String cluster, AwsConfig awsConfig, EcsResizeParams ecsResizeParams, boolean blueGreen,
      boolean timeoutErrorSupported) {
    super(accountId, appId, commandName, activityId, region, cluster, awsConfig, DEPLOY_ROLLBACK_DATA_FETCH,
        timeoutErrorSupported);
    this.ecsResizeParams = ecsResizeParams;
    this.blueGreen = blueGreen;
  }
}
