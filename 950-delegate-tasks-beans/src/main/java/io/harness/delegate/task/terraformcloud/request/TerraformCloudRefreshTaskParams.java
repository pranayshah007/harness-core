/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskType;
import io.harness.expression.Expression;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudRefreshTaskParams extends TerraformCloudTaskParams {
  String workspace;
  boolean discardPendingRuns;
  String message;
  @Expression(ALLOW_SECRETS) Map<String, String> variables;
  @Override
  public TerraformCloudTaskType getTaskType() {
    return TerraformCloudTaskType.RUN_REFRESH_STATE;
  }
}
