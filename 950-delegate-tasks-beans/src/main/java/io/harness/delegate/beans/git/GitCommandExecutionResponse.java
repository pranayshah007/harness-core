/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.git;

import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.connector.ConnectorValidationResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitBaseResult;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitCommandExecutionResponse implements ConnectorValidationResponseData {
  private GitBaseResult gitCommandResult;
  private GitBaseRequest gitCommandRequest;
  private GitCommandStatus gitCommandStatus;
  private String errorMessage;
  private ErrorCode errorCode;
  private DelegateMetaInfo delegateMetaInfo;
  private ConnectorValidationResult connectorValidationResult;

  public enum GitCommandStatus { SUCCESS, FAILURE }
}
