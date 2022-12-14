/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;
import static io.harness.rule.OwnerRule.IVAN;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceResponse;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.cd.ActiveServiceDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveServicesFilterParams;
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.google.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.CDP)
public class CDLicenseUsageImplTest extends CategoryTest {
  @Mock private TimeScaleDAL timeScaleDAL;
  @Mock private CDLicenseUsageDAL utils;
  @InjectMocks @Inject private CDLicenseUsageImpl cdLicenseUsage;

  private static final String accountIdentifier = "ACCOUNT_ID";
  private static final String orgIdentifier = "ORG_ID";
  private static final String projectIdentifier = "PROJECT_ID";
  private static final String serviceIdentifier = "SERVICE";
  private static final String serviceIdentifier2 = "SERVICE_2";
  private static final String serviceName = "SERVICE_NAME";
  private static final String serviceName2 = "SERVICE_NAME_2";
  public static final String orgName = "ORG_NAME";
  public static final String projectName = "PROJECT_NAME";
  private static final long DAYS_30_IN_MILLIS = 2592000000L;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetServiceInstancesBasedLicenseUsage() {
    long timeInMillis = 1234123412345L;
    when(utils.fetchServiceInstancesOver30Days(accountIdentifier)).thenReturn(10L);
    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 10));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);
    List<Services> servicesList = new ArrayList<>();
    servicesList.add(new Services(
        null, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, serviceName, false, null, null));
    when(timeScaleDAL.getNamesForServiceIds(
             accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceWithInstanceCountList)))
        .thenReturn(servicesList);

    ServiceInstanceUsageDTO licenseUsage =
        (ServiceInstanceUsageDTO) cdLicenseUsage.getLicenseUsage(accountIdentifier, ModuleType.CD, timeInMillis,
            CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICE_INSTANCES).build());

    assertThat(licenseUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(licenseUsage.getCdLicenseType()).isEqualTo(CDLicenseType.SERVICE_INSTANCES);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(10L);
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
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().get(0).getCount()).isEqualTo(10);
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

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 0));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 18));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 41));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 20));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 9));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetLicenseUsageMultipleServicesAndOneDeletedService() {
    long timeInMillis = 1234123412345L;
    List<ServiceInfraInfo> activeServiceList = new ArrayList<>();
    activeServiceList.add(new ServiceInfraInfo(null, serviceName, serviceIdentifier, null, null, null, null, null, null,
        null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    activeServiceList.add(new ServiceInfraInfo(null, serviceName2, serviceIdentifier2, null, null, null, null, null,
        null, null, null, null, accountIdentifier, orgIdentifier, projectIdentifier, null));
    when(timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(
             accountIdentifier, timeInMillis - DAYS_30_IN_MILLIS, timeInMillis))
        .thenReturn(activeServiceList);

    List<AggregateServiceUsageInfo> activeServiceWithInstanceCountList = new ArrayList<>();
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier, 9));
    activeServiceWithInstanceCountList.add(
        new AggregateServiceUsageInfo(orgIdentifier, projectIdentifier, serviceIdentifier2, 11));
    when(utils.fetchInstancesPerServiceOver30Days(accountIdentifier)).thenReturn(activeServiceWithInstanceCountList);

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

    assertThat(licenseUsage.getActiveServices().getCount()).isEqualTo(2);
    assertThat(licenseUsage.getActiveServices().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServices().getReferences().size()).isEqualTo(2);

    assertThat(licenseUsage.getServiceLicenses().getCount()).isEqualTo(2);
    assertThat(licenseUsage.getServiceLicenses().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getServiceLicenses().getReferences().size()).isEqualTo(2);

    assertThat(licenseUsage.getActiveServiceInstances().getCount()).isEqualTo(20);
    assertThat(licenseUsage.getActiveServiceInstances().getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(licenseUsage.getActiveServiceInstances().getReferences().size()).isEqualTo(2);

    Optional<ReferenceDTO> aliveService = licenseUsage.getActiveServices()
                                              .getReferences()
                                              .stream()
                                              .filter(service -> serviceIdentifier.equals(service.getIdentifier()))
                                              .findFirst();
    assertThat(aliveService.get()).isNotNull();
    assertThat(aliveService.get().getName()).isEqualTo(serviceName);

    Optional<ReferenceDTO> deletedService = licenseUsage.getActiveServices()
                                                .getReferences()
                                                .stream()
                                                .filter(service -> serviceIdentifier2.equals(service.getIdentifier()))
                                                .findFirst();
    assertThat(deletedService.get()).isNotNull();
    assertThat(deletedService.get().getName()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLicenseUsage() {
    PageableUsageRequestParams usageRequestParam =
        DefaultPageableUsageRequestParams.builder()
            .pageRequest(PageableUtils.getPageRequest(0, 30, Arrays.asList("serviceInstances", "ASC")))
            .filterParams(
                ActiveServicesFilterParams.builder().serviceName("all").serviceName("all").projectName("all").build())
            .build();
    long currentTimeMillis = System.currentTimeMillis();
    when(utils.fetchActiveServices(any()))
        .thenReturn(ActiveServiceResponse.<List<ActiveServiceBase>>builder()
                        .activeServiceItems(Collections.singletonList(ActiveServiceBase.builder()
                                                                          .identifier(serviceIdentifier)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .instanceCount(2)
                                                                          .lastDeployed(currentTimeMillis)
                                                                          .build()))
                        .totalCountOfItems(1)
                        .build());
    when(utils.fetchActiveServicesNameOrgAndProjectName(eq(accountIdentifier), any()))
        .thenReturn(Collections.singletonList(ActiveService.builder()
                                                  .identifier(serviceIdentifier)
                                                  .name(serviceName)
                                                  .orgName(orgName)
                                                  .orgIdentifier(orgIdentifier)
                                                  .projectName(projectName)
                                                  .projectIdentifier(projectIdentifier)
                                                  .lastDeployed(currentTimeMillis)
                                                  .instanceCount(1)
                                                  .build()));
    Page<ActiveServiceDTO> activeServiceDTOS = cdLicenseUsage.listLicenseUsage(
        accountIdentifier, ModuleType.CD, System.currentTimeMillis(), usageRequestParam);

    assertThat(activeServiceDTOS.getTotalElements()).isEqualTo(1);
    assertThat(activeServiceDTOS.getTotalPages()).isEqualTo(1);
    List<ActiveServiceDTO> content = activeServiceDTOS.getContent();
    assertThat(content.size()).isEqualTo(1);
    ActiveServiceDTO activeServiceDTO = content.get(0);
    assertThat(activeServiceDTO.getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(activeServiceDTO.getName()).isEqualTo(serviceName);
    assertThat(activeServiceDTO.getOrgName()).isEqualTo(orgName);
    assertThat(activeServiceDTO.getProjectName()).isEqualTo(projectName);
    assertThat(activeServiceDTO.getInstanceCount()).isEqualTo(1);
    assertThat(activeServiceDTO.getLastDeployed()).isEqualTo(currentTimeMillis);
    assertThat(activeServiceDTO.getLicensesConsumed()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLicenseUsageWithInvalidRequest() {
    PageableUsageRequestParams usageRequestParam =
        DefaultPageableUsageRequestParams.builder()
            .pageRequest(PageableUtils.getPageRequest(0, 30, Arrays.asList("serviceInstances", "ASC")))
            .filterParams(
                ActiveServicesFilterParams.builder().serviceName("all").serviceName("all").projectName("all").build())
            .build();

    when(utils.fetchActiveServices(any()))
        .thenThrow(new CgLicenseUsageException("Failed to fetch active services", new SQLException("Invalid query")));

    assertThatThrownBy(()
                           -> cdLicenseUsage.listLicenseUsage(
                               orgIdentifier, ModuleType.CD, System.currentTimeMillis(), usageRequestParam))
        .hasMessage("Failed to fetch active services")
        .isInstanceOf(CgLicenseUsageException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLicenseUsageInvalidAccountArgument() {
    PageableUsageRequestParams usageRequestParam =
        DefaultPageableUsageRequestParams.builder()
            .pageRequest(PageableUtils.getPageRequest(0, 30, Arrays.asList("serviceInstances", "ASC")))
            .filterParams(
                ActiveServicesFilterParams.builder().serviceName("all").serviceName("all").projectName("all").build())
            .build();

    assertThatThrownBy(
        () -> cdLicenseUsage.listLicenseUsage(null, ModuleType.CD, System.currentTimeMillis(), usageRequestParam))
        .hasMessage("Account Identifier cannot be null or empty")
        .isInstanceOf(IllegalArgumentException.class);
  }
}
