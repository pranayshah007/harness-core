/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import io.harness.connector.ConnectorValidationResult;

import java.util.Objects;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConnectorValidationResponseTaskIdDecorator implements ConnectorValidationResponseData {
  ConnectorValidationResponseData response;
  private String taskId;

  @Override
  public ConnectorValidationResult getConnectorValidationResult() {
    var result = response.getConnectorValidationResult();
    if (Objects.nonNull(result)) {
      result.setTaskId(taskId);
    }
    return result;
  }
}
