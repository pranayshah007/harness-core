/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.GcpServiceAccountServiceImpl;
import io.harness.ccm.billing.bigquery.BigQueryService;
import io.harness.ccm.config.GcpBillingAccount.GcpBillingAccountBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.ValidationResult;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Dataset;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class GcpBillingAccountServiceImplTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";

  private String billingAccountUuid = "BILLING_ACCOUNT_UUID";
  private String organizationSettingId = "ORGANIZATION_SETTING_ID";
  private String impersonatedServiceAccount = "impersonate-account-1@ccm-play.iam.gserviceaccount.com";
  private GcpOrganization gcpOrganization;

  private GcpBillingAccount gcpBillingAccount;
  private String bqProjectId = "BQ_PROJECT_ID";
  private String bqDatasetId = "BQ_DATASET_ID";
  private String dataSetLocation = "location";
  private String billingAccountId = "billingAccountId";

  @Mock private GcpBillingAccountDao gcpBillingAccountDao;
  @Mock private GcpOrganizationService gcpOrganizationService;
  @Mock private GcpServiceAccountServiceImpl gcpServiceAccountService;
  @Mock private BigQueryService bigQueryService;
  @Mock private BigQuery bigQuery;
  @Mock private Dataset dataset;
  @InjectMocks private GcpBillingAccountServiceImpl gcpBillingAccountService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() throws GeneralSecurityException, IOException {
    gcpOrganization = GcpOrganization.builder().serviceAccountEmail(impersonatedServiceAccount).build();
    when(gcpOrganizationService.get(eq(organizationSettingId))).thenReturn(gcpOrganization);

    gcpBillingAccount = getGcpBillingAccount(organizationSettingId);
    when(bigQueryService.get(eq(bqProjectId), eq(impersonatedServiceAccount))).thenReturn(bigQuery);
    when(dataset.getLocation()).thenReturn(dataSetLocation);
    when(bigQuery.getDataset(anyString())).thenReturn(dataset);
    when(bigQueryService.canAccessDataset(eq(bigQuery), eq(bqProjectId), eq(bqDatasetId)))
        .thenReturn(ValidationResult.builder().valid(true).build());
  }

  private GcpBillingAccount getGcpBillingAccount(String organizationSettingId) {
    GcpBillingAccountBuilder builder = GcpBillingAccount.builder().bqProjectId(bqProjectId).bqDatasetId(bqDatasetId);
    if (organizationSettingId != null) {
      builder.organizationSettingId(organizationSettingId);
    }
    return builder.build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreateForValidBillingAccount() {
    gcpBillingAccountService.create(gcpBillingAccount);
    verify(gcpBillingAccountDao).upsert(gcpBillingAccount);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCreateForInvalidBillingAccount() {
    GcpBillingAccount gcpBillingAccount = getGcpBillingAccount(null);
    gcpBillingAccountService.create(gcpBillingAccount);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldNotUpdateForInvalidBillingAccount() {
    GcpBillingAccount gcpBillingAccount = getGcpBillingAccount(null);
    gcpBillingAccountService.update(billingAccountId, gcpBillingAccount);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldNotUpdateForValidationFailure() {
    when(bigQueryService.canAccessDataset(eq(bigQuery), eq(bqProjectId), eq(bqDatasetId)))
        .thenReturn(ValidationResult.builder().valid(false).errorMessage("ErrorMessage").build());
    gcpBillingAccountService.update(billingAccountId, gcpBillingAccount);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGet() {
    gcpBillingAccountService.get(billingAccountUuid);
    verify(gcpBillingAccountDao).get(eq(billingAccountUuid));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testList() {
    gcpBillingAccountService.list(accountId, organizationSettingId);
    verify(gcpBillingAccountDao).list(eq(accountId), eq(organizationSettingId));
  }
}
