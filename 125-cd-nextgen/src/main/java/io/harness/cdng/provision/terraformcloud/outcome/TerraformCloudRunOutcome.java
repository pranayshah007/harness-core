/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import java.util.HashMap;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@RecasterAlias("io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome")
public class TerraformCloudRunOutcome implements Outcome {
  public static final String OUTCOME_NAME = "runData";
  Integer detailedExitCode;
  String jsonFilePath;
  String runId;
  HashMap<String, Object> outputs;
  String policyChecksFilePath;
}
