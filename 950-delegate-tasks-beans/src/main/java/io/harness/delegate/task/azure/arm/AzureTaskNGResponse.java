/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.NonFinal;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AzureTaskNGResponse implements DelegateTaskNotifyResponseData {
  CommandExecutionStatus commandExecutionStatus;
  @NonFinal @Setter UnitProgressData unitProgressData;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  String errorMsg;
}
