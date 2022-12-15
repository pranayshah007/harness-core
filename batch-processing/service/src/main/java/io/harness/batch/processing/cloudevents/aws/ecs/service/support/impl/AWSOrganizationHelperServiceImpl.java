/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AWSOrganizationHelperService;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.AWSOrganizationsClientBuilder;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AWSOrganizationHelperServiceImpl implements AWSOrganizationHelperService {
  @Autowired private AwsCredentialHelper awsCredentialHelper;

  private static final String ceAWSRegion = AWS_DEFAULT_REGION;

  @Override
  public List<Account> listAwsAccounts(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    try {
      AWSOrganizationsClient awsOrganizationsClient = getAWSOrganizationsClient(awsCrossAccountAttributes);
      return listAwsAccounts(awsOrganizationsClient);
    } catch (Exception ex) {
      log.error("Error while getting accounts", ex);
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  AWSOrganizationsClient getAWSOrganizationsClient(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    AWSOrganizationsClientBuilder builder = AWSOrganizationsClientBuilder.standard().withRegion(ceAWSRegion);
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider
            .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
            .withExternalId(awsCrossAccountAttributes.getExternalId())
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    return (AWSOrganizationsClient) builder.build();
  }

  private List<Account> listAwsAccounts(AWSOrganizationsClient awsOrganizationsClient) {
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
}
