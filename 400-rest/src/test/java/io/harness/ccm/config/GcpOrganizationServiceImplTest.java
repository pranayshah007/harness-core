/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.GcpServiceAccountService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;

import com.google.api.services.iam.v1.model.ServiceAccount;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class GcpOrganizationServiceImplTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String gcpOrganizationId1 = "GCP_ORGANIZATION_ID_1";
  private String organizationName1 = "ORGANIZATION_NAME_1";
  private String organizationName2 = "ORGANIZATION_NAME_2";
  private GcpOrganization gcpOrganization1;
  private String serviceAccountEmail = "SERVICE_ACCOUNT_EMAIL";

  private ServiceAccount serviceAccount = new ServiceAccount();

  @Captor ArgumentCaptor<GcpOrganization> gcpOrganizationCaptor;
  @Mock private GcpOrganizationDao gcpOrganizationDao;
  @Mock private GcpServiceAccountService gcpServiceAccountService;
  @Mock private SettingsService settingsService;
  @Mock private CeConnectorDao ceConnectorDao;
  @Mock private CEGcpServiceAccountService ceGcpServiceAccountService;
  @Mock private GcpBillingAccountService gcpBillingAccountService;
  @InjectMocks private GcpOrganizationServiceImpl gcpOrganizationService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() throws IOException {
    gcpOrganization1 = GcpOrganization.builder()
                           .accountId(accountId)
                           .organizationName(organizationName1)
                           .serviceAccountEmail(serviceAccountEmail)
                           .build();
    serviceAccount.setEmail(serviceAccountEmail);
    when(gcpServiceAccountService.create(anyString(), anyString())).thenReturn(serviceAccount);
    when(ceGcpServiceAccountService.getByAccountId(eq(accountId)))
        .thenReturn(GcpServiceAccount.builder().email(serviceAccountEmail).build());
    when(gcpOrganizationDao.upsert(any(GcpOrganization.class)))
        .thenReturn(GcpOrganization.builder().serviceAccountEmail(serviceAccountEmail).build());
    when(gcpOrganizationDao.save(any(GcpOrganization.class))).thenReturn("GCP_ORGANIZATION_UUID");
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldUpsert() {
    gcpOrganizationService.upsert(gcpOrganization1);
    verify(gcpOrganizationDao).upsert(gcpOrganizationCaptor.capture());
    assertThat(gcpOrganizationCaptor.getValue().getServiceAccountEmail()).isEqualTo(serviceAccountEmail);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldThrowIfUpsertBeyondLimit() {
    when(gcpOrganizationDao.count(eq(accountId))).thenReturn(2L);
    gcpOrganizationService.upsert(gcpOrganization1);
    gcpOrganizationService.upsert(gcpOrganization1);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGet() {
    gcpOrganizationService.get(gcpOrganizationId1);
    verify(gcpOrganizationDao).get(eq(gcpOrganizationId1));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldList() {
    gcpOrganizationService.list(accountId);
    verify(gcpOrganizationDao).list(accountId);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDelete() {
    String organizationUuid = "UUID";
    String settingAttributeUuid = "SETTING_ATTRIBUTE_UUID";
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(settingAttributeUuid).build();
    when(ceConnectorDao.getCEGcpConfig(eq(accountId), eq(organizationUuid))).thenReturn(settingAttribute);
    gcpOrganizationService.delete(accountId, organizationUuid);
    verify(settingsService).delete(eq(GLOBAL_APP_ID), eq(settingAttributeUuid));
    verify(gcpOrganizationDao).delete(eq(accountId), eq(organizationUuid));
  }
}
