/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.billingDataVerification.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationCost;
import io.harness.ccm.billingDataVerification.dto.CCMBillingDataVerificationKey;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEProxyConfig;

import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.Tag;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CE)
public interface BillingDataVerificationSQLService {
  Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> fetchAWSCostsFromAWSBillingTables(
      String accountId, ConnectorResponseDTO connector, String startDate, String endDate);
  Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> fetchAWSCostsFromUnifiedTable(
      String accountId, ConnectorResponseDTO connector, String startDate, String endDate);
  void ingestAWSCostsIntoBillingDataVerificationTable(
      String accountId, Map<CCMBillingDataVerificationKey, CCMBillingDataVerificationCost> billingData);
}
