/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.aggregates.AggregateNgServiceInstanceStats;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class CDLicenseUsageImplTest extends CategoryTest {
  @Mock private TimeScaleDAL timeScaleDAL;
  @InjectMocks @Inject private CDLicenseUsageImpl cdLicenseUsage;

  private static final String accountIdentifier = "ACCOUNT_ID";
  private static final String orgIdentifier = "ORG_ID";
  private static final String projectIdentifier = "PROJECT_ID";
  private static final String serviceIdentifier = "SERVICE";
  private static final String serviceIdentifier2 = "SERVICE_2";
  private static final String serviceName = "SERVICE_NAME";
  private static final String serviceName2 = "SERVICE_NAME_2";
  private static final long DAYS_30_IN_MILLIS = 2592000000L;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageOneServiceWithZeroInstances() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateNgServiceInstanceStats(orgIdentifier, projectIdentifier, serviceIdentifier, 0));
    when(timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(accountIdentifier, 0.95,
             timeInMillis - DAYS_30_IN_MILLIS, timeInMillis,
             NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(activeServiceWithInstanceCountList);

    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(servicesList);

    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getCount()).isEqualTo(1);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(0);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getIdentifier())
        .isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getOrgIdentifier())
        .isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageOneServiceWithLt20Instances() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateNgServiceInstanceStats(orgIdentifier, projectIdentifier, serviceIdentifier, 18));
    when(timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(accountIdentifier, 0.95,
             timeInMillis - DAYS_30_IN_MILLIS, timeInMillis,
             NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(activeServiceWithInstanceCountList);

    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(servicesList);

    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getCount()).isEqualTo(1);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(18);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getIdentifier())
        .isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getOrgIdentifier())
        .isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getCount()).isEqualTo(18);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageOneServiceWithGt20Instances() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateNgServiceInstanceStats(orgIdentifier, projectIdentifier, serviceIdentifier, 41));
    when(timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(accountIdentifier, 0.95,
             timeInMillis - DAYS_30_IN_MILLIS, timeInMillis,
             NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(activeServiceWithInstanceCountList);

    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(servicesList);

    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(3);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getCount()).isEqualTo(3);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(41);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getIdentifier())
        .isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getOrgIdentifier())
        .isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getCount()).isEqualTo(41);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageOneServiceWith20Instances() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateNgServiceInstanceStats(orgIdentifier, projectIdentifier, serviceIdentifier, 20));
    when(timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(accountIdentifier, 0.95,
             timeInMillis - DAYS_30_IN_MILLIS, timeInMillis,
             NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(activeServiceWithInstanceCountList);

    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(servicesList);

    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServices().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getServiceLicenses().getReferences().get(0).getCount()).isEqualTo(1);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(20);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(1);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getName()).isEqualTo(serviceName);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getIdentifier())
        .isEqualTo(serviceIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getAccountIdentifier())
        .isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getOrgIdentifier())
        .isEqualTo(orgIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getProjectIdentifier())
        .isEqualTo(projectIdentifier);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getCount()).isEqualTo(20);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageMultipleServices() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    activeServiceList.add(new ServiceInfraInfo(null, serviceName2, serviceIdentifier2, null, null, null, null, null,
        null, null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateNgServiceInstanceStats(orgIdentifier, projectIdentifier, serviceIdentifier, 9));
    when(timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(accountIdentifier, 0.95,
             timeInMillis - DAYS_30_IN_MILLIS, timeInMillis,
             NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(activeServiceWithInstanceCountList);

    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    servicesList.add(new Services(null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier2,
        serviceName2, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList)))
        .thenReturn(servicesList);

    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(2);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(2);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(2);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(2);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(9);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageInvalidTimestamp() {
    assertThatThrownBy(()
                           -> cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD, 0,
                               CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid timestamp 0 while fetching LicenseUsages.");
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageInvalidAccountIdentifier() {
    assertThatThrownBy(()
                           -> cdLicenseUsage.getLicenseUsage(StringUtils.EMPTY, ModuleType.CD, 1234123412345L,
                               CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Account Identifier cannot be null or blank");
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageWithNoActiveService() {
    long timeInMillis = 1234123412345L;
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(emptyList());
    ServiceUsageDTO licenseUsage = (ServiceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD,
        timeInMillis, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICES);

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(0);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(0);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(0);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(0);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(0);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(0);
  }
}
