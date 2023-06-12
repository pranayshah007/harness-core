/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.azure.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorValidationResponseData;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class AzureValidateTaskResponse extends AzureDelegateTaskResponse implements ConnectorValidationResponseData {
  private ConnectorValidationResult connectorValidationResult;
}
