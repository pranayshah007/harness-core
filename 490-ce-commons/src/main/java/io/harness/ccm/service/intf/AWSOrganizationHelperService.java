/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEProxyConfig;

import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.Tag;
import java.util.List;

@OwnedBy(HarnessTeam.CE)
public interface AWSOrganizationHelperService {
  List<CECloudAccount> getAWSAccounts(String accountId, String connectorId, CEAwsConnectorDTO ceAwsConnectorDTO,
      String awsAccessKey, String awsSecretKey, CEProxyConfig ceProxyConfig,
      CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig);
  List<Tag> listAwsAccountTags(AWSOrganizationsClient awsOrganizationsClient, String awsAccountId);
  List<Account> listAwsAccounts(CrossAccountAccessDTO crossAccountAccess, String awsAccessKey, String awsSecretKe,
      CEProxyConfig ceProxyConfig, CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig);
  List<Account> listAwsAccounts(AWSOrganizationsClient awsOrganizationsClient);
}
