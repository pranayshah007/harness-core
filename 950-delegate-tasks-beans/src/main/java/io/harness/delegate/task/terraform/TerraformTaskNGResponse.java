/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedRecordData;

import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(CDP)
public class TerraformTaskNGResponse implements DelegateTaskNotifyResponseData {
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;
  @NonFinal @Setter UnitProgressData unitProgressData;

  Map<String, String> commitIdForConfigFilesMap;
  EncryptedRecordData encryptedTfPlan;
  String outputs;
  String stateFileId;
  Integer detailedExitCode;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  String tfPlanJsonFileId;
  String tfHumanReadablePlanFileId;
  Map<String, Map<String, String>> keyVersionMap;
}
