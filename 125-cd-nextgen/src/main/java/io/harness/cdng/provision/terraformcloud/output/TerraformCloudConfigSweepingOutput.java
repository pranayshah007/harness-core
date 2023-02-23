/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.output;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig;
import io.harness.delegate.beans.terraformcloud.RollbackType;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("TerraformCloudConfigSweepingOutput")
@JsonTypeName("TerraformCloudConfigSweepingOutput")
@RecasterAlias("io.harness.cdng.provision.terraformcloud.output.TerraformCloudConfigSweepingOutput")
public class TerraformCloudConfigSweepingOutput implements ExecutionSweepingOutput {
  TerraformCloudConfig terraformCloudConfig;
  RollbackType rollbackTaskType;
}
