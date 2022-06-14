/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ExecuteCommandResponse;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(CDP)
public class CommandTaskResponse implements DelegateResponseData {
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  ExecuteCommandResponse executeCommandResponse;
  CommandExecutionStatus status;
  String errorMessage;
  UnitProgressData unitProgressData;
}
