/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanHumanReadableOutputStream;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@OwnedBy(CDP)
public class TerraformExecuteStepRequest {
  String tfBackendConfigsFile;
  String tfOutputsFile;
  List<String> tfVarFilePaths;
  String varParams; // Needed to send inline variable values in CG
  String uiLogs; // Needed to NOT log secrets in CG
  @Nonnull @NotEmpty String scriptDirectory;
  Map<String, String> envVars;
  List<String> targets;
  String workspace;
  boolean isRunPlanOnly; // Needed to send inline variable values in CG
  EncryptedRecordData encryptedTfPlan;
  EncryptionConfig encryptionConfig;
  @Builder.Default boolean isSkipRefreshBeforeApplyingPlan = true;
  boolean isSaveTerraformJson;
  boolean isSaveTerraformHumanReadablePlan;
  boolean useOptimizedTfPlan;
  @Nonnull LogCallback logCallback;
  @Nonnull PlanJsonLogOutputStream planJsonLogOutputStream;
  PlanHumanReadableOutputStream planHumanReadableOutputStream;
  @Nonnull PlanLogOutputStream planLogOutputStream;
  boolean analyseTfPlanSummary;
  long timeoutInMillis;
  boolean isTfPlanDestroy;
  String accountId;
  boolean isTerraformCloudCli;
  boolean skipTerraformRefresh; // NG
  Map<String, String> additionalCliFlags;
}
