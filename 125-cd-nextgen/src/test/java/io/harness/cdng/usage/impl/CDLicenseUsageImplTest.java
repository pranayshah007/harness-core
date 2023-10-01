/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;
import static io.harness.rule.OwnerRule.IVAN;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.CDLicenseUsageReportService;
import io.harness.cdng.usage.dto.LicenseDateUsageDTO;
import io.harness.cdng.usage.dto.LicenseDateUsageParams;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.cd.ActiveServiceDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveServicesFilterParams;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.google.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;

@OwnedBy(CDP)
public class CDLicenseUsageImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG = "Account Identifier cannot be null or empty";

  @Mock private TimeScaleDAL timeScaleDAL;
  @Mock private CDLicenseUsageDAL utils;
  @Mock private CDLicenseUsageReportService cdLicenseUsageReportService;
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
            .filterParams(ActiveServicesFilterParams.builder()
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .serviceIdentifier(serviceIdentifier)
                              .build())
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
    when(utils.fetchActiveServicesNameOrgAndProjectName(eq(accountIdentifier), any(), any()))
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
    assertThat(activeServiceDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(activeServiceDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
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
            .filterParams(ActiveServicesFilterParams.builder()
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .serviceIdentifier(serviceIdentifier)
                              .build())
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
            .filterParams(ActiveServicesFilterParams.builder()
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .serviceIdentifier(serviceIdentifier)
                              .build())
            .build();

    assertThatThrownBy(
        () -> cdLicenseUsage.listLicenseUsage(null, ModuleType.CD, System.currentTimeMillis(), usageRequestParam))
        .hasMessage(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG)
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportWithInvalidRequest() {
    when(utils.fetchActiveServices(any()))
        .thenThrow(new CgLicenseUsageException("Failed to fetch active services", new SQLException("Invalid query")));

    assertThatThrownBy(
        () -> cdLicenseUsage.getLicenseUsageCSVReport(orgIdentifier, ModuleType.CD, System.currentTimeMillis()))
        .hasMessage("Failed to fetch active services")
        .isInstanceOf(CgLicenseUsageException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidAccountArgument() {
    assertThatThrownBy(() -> cdLicenseUsage.getLicenseUsageCSVReport(null, ModuleType.CD, System.currentTimeMillis()))
        .hasMessage(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG)
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidModule() {
    assertThatThrownBy(
        () -> cdLicenseUsage.getLicenseUsageCSVReport(accountIdentifier, ModuleType.SRM, System.currentTimeMillis()))
        .hasMessage("Invalid Module type SRM provided, expected CD")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidTimestamp() {
    assertThatThrownBy(() -> cdLicenseUsage.getLicenseUsageCSVReport(accountIdentifier, ModuleType.CD, 0))
        .hasMessage("Invalid timestamp 0 while downloading CD active services report")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReport() throws IOException {
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
    when(utils.fetchActiveServicesNameOrgAndProjectName(eq(accountIdentifier), any(), any()))
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

    File licenseUsageCSVReport = new File("path");
    try {
      licenseUsageCSVReport =
          cdLicenseUsage.getLicenseUsageCSVReport(accountIdentifier, ModuleType.CD, System.currentTimeMillis());

      Reader in = new FileReader(licenseUsageCSVReport.getPath());
      Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
      List<CSVRecord> csvRecords = new ArrayList<>();
      for (CSVRecord record : records) {
        csvRecords.add(record);
      }
      CSVRecord header = csvRecords.get(0);
      assertThat(header.get(0)).isEqualTo("SERVICE");
      assertThat(header.get(1)).isEqualTo("ORGANIZATIONS");
      assertThat(header.get(2)).isEqualTo("PROJECTS");
      assertThat(header.get(3)).isEqualTo("SERVICE ID");
      assertThat(header.get(4)).isEqualTo("SERVICE INSTANCES");
      assertThat(header.get(5)).isEqualTo("LAST DEPLOYED");
      assertThat(header.get(6)).isEqualTo("LICENSES CONSUMED");

      CSVRecord activeServiceRecord = csvRecords.get(1);
      assertThat(activeServiceRecord.get(0)).isEqualTo(serviceName);
      assertThat(activeServiceRecord.get(1)).isEqualTo(orgName);
      assertThat(activeServiceRecord.get(2)).isEqualTo(projectName);
      assertThat(activeServiceRecord.get(3)).isEqualTo(serviceIdentifier);
      assertThat(activeServiceRecord.get(4)).isEqualTo("1");
      assertThat(activeServiceRecord.get(5)).isEqualTo(String.valueOf(currentTimeMillis));
      assertThat(activeServiceRecord.get(6)).isEqualTo("1");
    } finally {
      deleteFileIfExists(licenseUsageCSVReport.getPath());
    }
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetServiceInstancesDateUsageInvalidAccountArgument() {
    assertThatThrownBy(()
                           -> cdLicenseUsage.getLicenseDateUsage(
                               null, LicenseDateUsageParams.builder().build(), CDLicenseType.SERVICE_INSTANCES))
        .hasMessage(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG)
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetServiceInstancesDateUsageNotValidaFromDate() {
    assertThatThrownBy(()
                           -> cdLicenseUsage.getLicenseDateUsage(accountIdentifier,
                               LicenseDateUsageParams.builder()
                                   .fromDate("2022-14-01")
                                   .toDate("2022-01-08")
                                   .reportType(LicenseDateUsageReportType.DAILY)
                                   .build(),
                               CDLicenseType.SERVICE_INSTANCES))
        .hasMessage("Invalid date format, pattern: yyyy-MM-dd, date: 2022-14-01")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetServiceInstancesDateUsageNotValidaToDate() {
    assertThatThrownBy(()
                           -> cdLicenseUsage.getLicenseDateUsage(accountIdentifier,
                               LicenseDateUsageParams.builder()
                                   .fromDate("2022-01-01")
                                   .toDate("2022-14-08")
                                   .reportType(LicenseDateUsageReportType.DAILY)
                                   .build(),
                               CDLicenseType.SERVICE_INSTANCES))
        .hasMessage("Invalid date format, pattern: yyyy-MM-dd, date: 2022-14-08")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseDateUsageMonthly() {
    when(cdLicenseUsageReportService.getLicenseUsagePerMonthsReport(any(), any(), any(), any()))
        .thenReturn(getMonthlyServiceUsageReport());
    ArgumentCaptor<String> accountIdentifierCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CDLicenseType> licenseTypeCaptor = ArgumentCaptor.forClass(CDLicenseType.class);
    ArgumentCaptor<LocalDate> fromDayCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toDayCaptor = ArgumentCaptor.forClass(LocalDate.class);

    LicenseDateUsageDTO serviceInstancesDateUsage = cdLicenseUsage.getLicenseDateUsage(accountIdentifier,
        LicenseDateUsageParams.builder()
            .fromDate("2022-01-01")
            .toDate("2022-08-01")
            .reportType(LicenseDateUsageReportType.MONTHLY)
            .build(),
        CDLicenseType.SERVICE_INSTANCES);

    verify(cdLicenseUsageReportService, times(1))
        .getLicenseUsagePerMonthsReport(accountIdentifierCaptor.capture(), licenseTypeCaptor.capture(),
            fromDayCaptor.capture(), toDayCaptor.capture());

    assertThat(serviceInstancesDateUsage).isNotNull();
    assertThat(serviceInstancesDateUsage.getLicenseUsage().size()).isEqualTo(8);

    Integer serviceInstancesLastDay = serviceInstancesDateUsage.getLicenseUsage().get("2022-08-01");
    assertThat(serviceInstancesLastDay).isEqualTo(41);

    assertThat(licenseTypeCaptor.getValue()).isEqualTo(CDLicenseType.SERVICE_INSTANCES);
    assertThat(fromDayCaptor.getValue()).isEqualTo("2022-01-01");
    assertThat(toDayCaptor.getValue()).isEqualTo("2022-08-01");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseDateUsageDaily() {
    when(cdLicenseUsageReportService.getLicenseUsagePerDaysReport(any(), any(), any(), any()))
        .thenReturn(getDailyServiceUsageReport());
    ArgumentCaptor<String> accountIdentifierCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CDLicenseType> licenseTypeCaptor = ArgumentCaptor.forClass(CDLicenseType.class);
    ArgumentCaptor<LocalDate> fromDayCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toDayCaptor = ArgumentCaptor.forClass(LocalDate.class);
    LicenseDateUsageDTO serviceInstancesDateUsage = cdLicenseUsage.getLicenseDateUsage(accountIdentifier,
        LicenseDateUsageParams.builder()
            .fromDate("2022-01-01")
            .toDate("2022-01-08")
            .reportType(LicenseDateUsageReportType.DAILY)
            .build(),
        CDLicenseType.SERVICES);

    verify(cdLicenseUsageReportService, times(1))
        .getLicenseUsagePerDaysReport(accountIdentifierCaptor.capture(), licenseTypeCaptor.capture(),
            fromDayCaptor.capture(), toDayCaptor.capture());

    assertThat(serviceInstancesDateUsage).isNotNull();
    assertThat(serviceInstancesDateUsage.getLicenseUsage().size()).isEqualTo(8);

    Integer serviceInstancesLastDay = serviceInstancesDateUsage.getLicenseUsage().get("2022-01-08");
    assertThat(serviceInstancesLastDay).isEqualTo(41);

    assertThat(licenseTypeCaptor.getValue()).isEqualTo(CDLicenseType.SERVICES);
    assertThat(fromDayCaptor.getValue()).isEqualTo("2022-01-01");
    assertThat(toDayCaptor.getValue()).isEqualTo("2022-01-08");
  }

  @NotNull
  private Map<String, Integer> getDailyServiceUsageReport() {
    Map<String, Integer> serviceInstancesUsage = new LinkedHashMap<>();
    serviceInstancesUsage.put("2022-01-01", 1);
    serviceInstancesUsage.put("2022-01-02", 2);
    serviceInstancesUsage.put("2022-01-03", 3);
    serviceInstancesUsage.put("2022-01-04", 4);
    serviceInstancesUsage.put("2022-01-05", 5);
    serviceInstancesUsage.put("2022-01-06", 1);
    serviceInstancesUsage.put("2022-01-07", 10);
    serviceInstancesUsage.put("2022-01-08", 41);
    return serviceInstancesUsage;
  }

  @NotNull
  private Map<String, Integer> getMonthlyServiceUsageReport() {
    Map<String, Integer> serviceInstancesUsage = new LinkedHashMap<>();
    serviceInstancesUsage.put("2022-01-01", 1);
    serviceInstancesUsage.put("2022-02-01", 2);
    serviceInstancesUsage.put("2022-03-01", 3);
    serviceInstancesUsage.put("2022-04-01", 4);
    serviceInstancesUsage.put("2022-05-01", 5);
    serviceInstancesUsage.put("2022-06-01", 1);
    serviceInstancesUsage.put("2022-07-01", 10);
    serviceInstancesUsage.put("2022-08-01", 41);
    return serviceInstancesUsage;
  }
}
