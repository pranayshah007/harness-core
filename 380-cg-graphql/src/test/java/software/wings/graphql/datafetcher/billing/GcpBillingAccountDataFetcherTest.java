/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.GcpBillingAccountQueryArguments;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpBillingAccountDTO;
import io.harness.ccm.config.GcpBillingAccountService;
import io.harness.rule.Owner;

import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import graphql.GraphQLContext;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class GcpBillingAccountDataFetcherTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String uuid = "UUID";
  private String organizationSettingId = "ORGANIZATION_SETTING_ID";
  private GcpBillingAccount gcpBillingAccount;
  private List<GcpBillingAccount> gcpBillingAccounts;
  private GcpBillingAccountDTO gcpBillingAccountDTO;
  private List<GcpBillingAccountDTO> gcpBillingAccountDTOs;

  @Mock private GraphQLContext graphQLContext;
  @Mock CeAccountExpirationChecker accountChecker;

  @Mock GcpBillingAccountService gcpBillingAccountService;
  @InjectMocks GcpBillingAccountDataFetcher gcpBillingAccountDataFetcher;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    gcpBillingAccount = GcpBillingAccount.builder().build();
    gcpBillingAccountDTO = GcpBillingAccountDTO.builder().build();
    gcpBillingAccounts = Arrays.asList(gcpBillingAccount);
    gcpBillingAccountDTOs = Arrays.asList(gcpBillingAccountDTO);
    when(graphQLContext.get(eq("accountId"))).thenReturn(accountId);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetGcpBillingAccount() throws Exception {
    when(gcpBillingAccountService.get(eq(uuid))).thenReturn(gcpBillingAccount);
    GcpBillingAccountQueryArguments arguments = new GcpBillingAccountQueryArguments(uuid, organizationSettingId);
    List<GcpBillingAccountDTO> actuals = gcpBillingAccountDataFetcher.fetch(arguments, accountId);
    assertThat(actuals).contains(gcpBillingAccountDTO);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldListGcpBillingAccount() throws Exception {
    when(gcpBillingAccountService.list(eq(accountId), eq(organizationSettingId))).thenReturn(gcpBillingAccounts);
    GcpBillingAccountQueryArguments arguments = new GcpBillingAccountQueryArguments(null, organizationSettingId);
    List<GcpBillingAccountDTO> actuals = gcpBillingAccountDataFetcher.fetch(arguments, accountId);
    assertThat(actuals).containsAll(gcpBillingAccountDTOs);
  }
}
