/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

@OwnedBy(CDC)
public interface ServiceNowApprovalHelperService {
  ServiceNowConnectorDTO getServiceNowConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef);

  void handlePollingEvent(ServiceNowApprovalInstance serviceNowApprovalInstance);
}
