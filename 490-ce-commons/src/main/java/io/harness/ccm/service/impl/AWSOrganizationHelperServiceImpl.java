/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClientImpl;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.service.intf.AWSOrganizationHelperService;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEProxyConfig;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import com.amazonaws.services.organizations.model.ListTagsForResourceRequest;
import com.amazonaws.services.organizations.model.ListTagsForResourceResult;
import com.amazonaws.services.organizations.model.Tag;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class AWSOrganizationHelperServiceImpl implements AWSOrganizationHelperService {
  @Inject AwsClientImpl awsClient;

  @Override
  public List<CECloudAccount> getAWSAccounts(String accountId, String connectorId, CEAwsConnectorDTO ceAwsConnectorDTO,
      String awsAccessKey, String awsSecretKey, CEProxyConfig ceProxyConfig,
      CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig) {
    List<CECloudAccount> ceCloudAccounts = new ArrayList<>();
    CrossAccountAccessDTO crossAccountAccess = ceAwsConnectorDTO.getCrossAccountAccess();
    List<Account> accountList =
        listAwsAccounts(crossAccountAccess, awsAccessKey, awsSecretKey, ceProxyConfig, ceAwsServiceEndpointConfig);
    String masterAwsAccountId = getMasterAccountIdFromArn(crossAccountAccess.getCrossAccountRoleArn());
    accountList.forEach(account -> {
      String awsAccountId = getAccountIdFromArn(account.getArn());
      if (!awsAccountId.equals(masterAwsAccountId)) {
        CECloudAccount cloudAccount =
            CECloudAccount.builder()
                .accountId(accountId)
                .accountArn(account.getArn())
                .accountStatus(CECloudAccount.AccountStatus.NOT_VERIFIED)
                .accountName(account.getName())
                .infraAccountId(awsAccountId)
                .infraMasterAccountId(ceAwsConnectorDTO.getAwsAccountId())
                .masterAccountSettingId(connectorId)
                .awsCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                               .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
                                               .externalId(crossAccountAccess.getExternalId())
                                               .build())
                .build();
        ceCloudAccounts.add(cloudAccount);
      }
    });
    log.info("CE cloud account {}", ceCloudAccounts);
    return ceCloudAccounts;
  }

  public List<Account> listAwsAccounts(CrossAccountAccessDTO crossAccountAccess, String awsAccessKey,
      String awsSecretKey, CEProxyConfig ceProxyConfig, CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig) {
    AWSOrganizationsClient awsOrganizationsClient =
        awsClient.getAWSOrganizationsClient(crossAccountAccess.getCrossAccountRoleArn(),
            crossAccountAccess.getExternalId(), awsAccessKey, awsSecretKey, ceProxyConfig, ceAwsServiceEndpointConfig);
    return listAwsAccounts(awsOrganizationsClient);
  }

  public static String getAccountIdFromArn(String arn) {
    return StringUtils.substringAfterLast(arn, "/");
  }

  public static String getMasterAccountIdFromArn(String arn) {
    return StringUtils.substringBefore(StringUtils.substringAfterLast(arn, "iam::"), ":");
  }

  public List<Account> listAwsAccounts(AWSOrganizationsClient awsOrganizationsClient) {
    List<Account> accountList = new ArrayList<>();
    String nextToken = null;
    ListAccountsRequest listAccountsRequest = new ListAccountsRequest();
    do {
      listAccountsRequest.withNextToken(nextToken);
      ListAccountsResult listAccountsResult = awsOrganizationsClient.listAccounts(listAccountsRequest);
      accountList.addAll(listAccountsResult.getAccounts());
      nextToken = listAccountsResult.getNextToken();
    } while (nextToken != null);
    return accountList;
  }

  public List<Tag> listAwsAccountTags(AWSOrganizationsClient awsOrganizationsClient, String awsAccountId) {
    List<Tag> tagList = new ArrayList<>();
    String nextToken = null;
    ListTagsForResourceRequest listTagsForResourceRequest = new ListTagsForResourceRequest();
    do {
      listTagsForResourceRequest.withNextToken(nextToken);
      ListTagsForResourceResult listTagsForResourceResult =
          awsOrganizationsClient.listTagsForResource(listTagsForResourceRequest.withResourceId(awsAccountId));
      tagList.addAll(listTagsForResourceResult.getTags());
      nextToken = listTagsForResourceResult.getNextToken();
    } while (nextToken != null);
    return tagList;
  }
}
