/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskhandlers;

import io.harness.delegate.beans.connector.gcp.GcpValidationParams;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;

public class GcpRequestMapper {
  public GcpRequest toGcpRequest(GcpValidationParams gcpValidationParams) {
    return GcpValidationRequest.builder()
        .gcpManualDetailsDTO((GcpManualDetailsDTO) gcpValidationParams.getGcpConnectorDTO().getCredential().getConfig())
        .delegateSelectors(gcpValidationParams.getDelegateSelectors())
        .encryptionDetails(gcpValidationParams.getEncryptionDetails())
        .build();
  }
}
