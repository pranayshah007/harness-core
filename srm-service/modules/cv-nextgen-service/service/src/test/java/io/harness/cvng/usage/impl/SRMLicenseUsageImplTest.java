/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CvNextGenTestBase;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.usage.impl.resources.ActiveServiceDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.CV)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SRMLicenseUsageImplTest extends CvNextGenTestBase {
  @Inject MetricPackService metricPackService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private LicenseUsageInterface licenseUsageInterface;
  @Mock private FeatureFlagService featureFlagService;
  private BuilderFactory builderFactory;
  private static final String ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG = "Account Identifier cannot be null or empty";

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  public void testGetLicenseUsage() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    doReturn(true).when(featureFlagService).isFeatureFlagEnabled(any(), any());
    SRMLicenseUsageDTO SRMLicenseUsageDTO = (SRMLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.CV, 1, null);
    assertThat(SRMLicenseUsageDTO.getActiveServices().getCount()).isEqualTo(2);
    assertThat(SRMLicenseUsageDTO.getActiveServices().getDisplayName()).isEqualTo("Total active SRM services");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsage() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms3", true);
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(3);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("service1");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceDTOList.get(1).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(1).getMonitoredServiceCount()).isEqualTo(2);
    assertThat(activeServiceDTOList.get(2).getIdentifier()).isEqualTo("service3");
    assertThat(activeServiceDTOList.get(2).getMonitoredServiceCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsageAccountLevel() {
    MonitoredServiceDTO monitoredServiceDTO =
        createMonitoredServiceDTOBuilder("ms1", "account.service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 =
        createMonitoredServiceDTOBuilder("ms12", "account.service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms3", true);
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(4);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("account.service1");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceDTOList.get(0).getOrgName()).isEqualTo("Deleted");
    assertThat(activeServiceDTOList.get(0).getProjectName()).isEqualTo("Deleted");
    assertThat(activeServiceDTOList.get(1).getIdentifier()).isEqualTo("account.service2");
    assertThat(activeServiceDTOList.get(1).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceDTOList.get(2).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(2).getMonitoredServiceCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsageOrgLevel() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "org.service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "org.service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms13", true);
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(3);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("org.service1");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceDTOList.get(0).getOrgName()).isEqualTo("Mocked org name");
    assertThat(activeServiceDTOList.get(0).getProjectName()).isEqualTo("Deleted");
    assertThat(activeServiceDTOList.get(1).getIdentifier()).isEqualTo("org.service2");
    assertThat(activeServiceDTOList.get(1).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceDTOList.get(1).getOrgName()).isEqualTo("Mocked org name");
    assertThat(activeServiceDTOList.get(1).getProjectName()).isEqualTo("Deleted");
    assertThat(activeServiceDTOList.get(2).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(2).getMonitoredServiceCount()).isEqualTo(2);
    assertThat(activeServiceDTOList.get(2).getOrgName()).isEqualTo("Mocked org name");
    assertThat(activeServiceDTOList.get(2).getProjectName()).isEqualTo("Mocked project name");
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsage_serviceFilter() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms13", true);
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().serviceIdentifier("service2").build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsage_projectFilter() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceDTO1.setProjectIdentifier("newProject");
    metricPackService.createDefaultMetricPackAndThresholds(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(), "newProject");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceDTO2.setProjectIdentifier("newProject");
    metricPackService.createDefaultMetricPackAndThresholds(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(), "newProject");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    ProjectParams newParams = builderFactory.getProjectParams();
    newParams.setProjectIdentifier("newProject");
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms13", true);
    PageableUsageRequestParams usageRequestParams = DefaultPageableUsageRequestParams.builder()
                                                        .filterParams(ActiveServiceMonitoredFilterParams.builder()
                                                                          .serviceIdentifier("service2")
                                                                          .projectIdentifier("newProject")
                                                                          .build())
                                                        .pageRequest(Pageable.ofSize(10))
                                                        .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(0).getProjectName()).isEqualTo("Mocked project name");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testListLicenseUsage_orgFilter() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceDTO1.setOrgIdentifier("newOrg");
    metricPackService.createDefaultMetricPackAndThresholds(
        builderFactory.getContext().getAccountId(), "newOrg", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceDTO3.setOrgIdentifier("newOrg");
    metricPackService.createDefaultMetricPackAndThresholds(
        builderFactory.getContext().getAccountId(), "newOrg", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    ProjectParams newParams = builderFactory.getProjectParams();
    newParams.setOrgIdentifier("newOrg");
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms2", true);
    PageableUsageRequestParams usageRequestParams = DefaultPageableUsageRequestParams.builder()
                                                        .filterParams(ActiveServiceMonitoredFilterParams.builder()
                                                                          .serviceIdentifier("service2")
                                                                          .orgIdentifier("newOrg")
                                                                          .build())
                                                        .pageRequest(Pageable.ofSize(10))
                                                        .build();
    Page<ActiveServiceDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceDTO> activeServiceDTOList = new ArrayList<>(result.toList());
    activeServiceDTOList.sort(Comparator.comparing(ActiveServiceDTO::getIdentifier));
    assertThat(activeServiceDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceDTOList.get(0).getOrgName()).isEqualTo("Mocked org name");
    assertThat(activeServiceDTOList.get(0).getMonitoredServiceCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReport() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    File file =
        licenseUsageInterface.getLicenseUsageCSVReport(builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l);
    assertThat(file.exists()).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidAccountArgument() {
    assertThatThrownBy(
        () -> licenseUsageInterface.getLicenseUsageCSVReport(null, ModuleType.SRM, System.currentTimeMillis()))
        .hasMessage(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG)
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidModule() {
    assertThatThrownBy(()
                           -> licenseUsageInterface.getLicenseUsageCSVReport(
                               builderFactory.getContext().getAccountId(), ModuleType.CD, System.currentTimeMillis()))
        .hasMessage("Invalid Module type CD provided, expected SRM")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = OwnerRule.SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidTimestamp() {
    assertThatThrownBy(()
                           -> licenseUsageInterface.getLicenseUsageCSVReport(
                               builderFactory.getContext().getAccountId(), ModuleType.SRM, 0))
        .hasMessage("Invalid timestamp 0 while downloading SRM active services monitored")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder(
      String monitoredServiceIdentifier, String serviceIdentifier, String environmentIdentifier) {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name("test");
  }
}
