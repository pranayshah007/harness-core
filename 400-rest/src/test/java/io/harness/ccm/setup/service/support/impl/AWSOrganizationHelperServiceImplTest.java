/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.AWSOrganizationsException;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AWSOrganizationHelperServiceImplTest extends CategoryTest {
  @Spy private AWSOrganizationHelperServiceImpl awsOrganizationHelperService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListAwsAccounts() {
    Account accountName1 = new Account()
                               .withName("account_name_1")
                               .withArn("arn:aws:organizations::12212121:account/o-tbm3caqef8/424324243");
    Account accountName2 = new Account()
                               .withName("account_name_2")
                               .withArn("arn:aws:organizations::12212121:account/o-tbm3caqef8/124324243");
    Account accountName3 = new Account()
                               .withName("account_name_3")
                               .withArn("arn:aws:organizations::12212121:account/o-tbm3caqef8/124324244");

    String nextToken = "nextOrgToken";
    AWSOrganizationsClient mockClient = mock(AWSOrganizationsClient.class);
    doReturn(mockClient).when(awsOrganizationHelperService).getAWSOrganizationsClient(any());
    doReturn(new ListAccountsResult().withAccounts(accountName1, accountName2).withNextToken(nextToken))
        .when(mockClient)
        .listAccounts(new ListAccountsRequest().withNextToken(null));

    doReturn(new ListAccountsResult().withAccounts(accountName3).withNextToken(null))
        .when(mockClient)
        .listAccounts(new ListAccountsRequest().withNextToken(nextToken));
    List<Account> accountList = awsOrganizationHelperService.listAwsAccounts(any());
    assertThat(accountList).hasSize(3);
    assertThat(accountList.get(0)).isEqualTo(accountName1);
    assertThat(accountList.get(1)).isEqualTo(accountName2);
    assertThat(accountList.get(2)).isEqualTo(accountName3);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListEmptyAwsAccountsWhenExceptionOccur() {
    AWSOrganizationsClient mockClient = mock(AWSOrganizationsClient.class);
    doReturn(mockClient).when(awsOrganizationHelperService).getAWSOrganizationsClient(any());

    doThrow(new AWSOrganizationsException("Invalid org"))
        .when(mockClient)
        .listAccounts(new ListAccountsRequest().withNextToken(null));
    List<Account> accountList = awsOrganizationHelperService.listAwsAccounts(any());
    assertThat(accountList).hasSize(0);
  }
}
