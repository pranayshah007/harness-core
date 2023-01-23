/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.k8s;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sTaskExecutionResponse implements CITaskExecutionResponse {
  private DelegateMetaInfo delegateMetaInfo;
  private CiK8sTaskResponse k8sTaskResponse;
  private String errorMessage;
  private CommandExecutionStatus commandExecutionStatus;
  private UnitProgressData commandUnitsProgress;
  @Builder.Default private static final CITaskExecutionResponse.Type type = Type.K8;

  @Override
  public CITaskExecutionResponse.Type getType() {
    return type;
  }
}
