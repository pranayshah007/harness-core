/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder()
@JsonTypeName("cloudFormationRollbackCompletionFlag")
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class CloudFormationRollbackCompletionFlag implements SweepingOutput {
  private boolean rollbackCompleted;

  @Override
  public String getType() {
    return "cloudFormationRollbackCompletionFlag";
  }
}
