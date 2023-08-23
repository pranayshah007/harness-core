/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.Ignore;
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
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  @Ignore(value = "Deprecated for now")
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
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceMonitoredDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(2);
    List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList = new ArrayList<>(result.toList());
    activeServiceMonitoredDTOList.sort(Comparator.comparing(ActiveServiceMonitoredDTO::getIdentifier));
    assertThat(activeServiceMonitoredDTOList.get(0).getIdentifier()).isEqualTo("service1");
    assertThat(activeServiceMonitoredDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
    assertThat(activeServiceMonitoredDTOList.get(1).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceMonitoredDTOList.get(1).getMonitoredServiceCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  @Ignore(value = "Deprecated as of now")
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
    PageableUsageRequestParams usageRequestParams =
        DefaultPageableUsageRequestParams.builder()
            .filterParams(ActiveServiceMonitoredFilterParams.builder().serviceIdentifier("service2").build())
            .pageRequest(Pageable.ofSize(10))
            .build();
    Page<ActiveServiceMonitoredDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList = new ArrayList<>(result.toList());
    activeServiceMonitoredDTOList.sort(Comparator.comparing(ActiveServiceMonitoredDTO::getIdentifier));
    assertThat(activeServiceMonitoredDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceMonitoredDTOList.get(0).getMonitoredServiceCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  @Ignore(value = "Deprecated as of now")
  public void testListLicenseUsage_projectFilter() {
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder("ms1", "service1", "env1").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTOBuilder("ms12", "service2", "env1").build();
    monitoredServiceDTO1.setProjectIdentifier("newProject");
    metricPackService.createDefaultMetricPackAndThresholds(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(), "newProject");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTOBuilder("ms13", "service2", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    MonitoredServiceDTO monitoredServiceDTO3 = createMonitoredServiceDTOBuilder("ms2", "service2", "env2").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    ProjectParams newParams = builderFactory.getProjectParams();
    newParams.setProjectIdentifier("newProject");
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    PageableUsageRequestParams usageRequestParams = DefaultPageableUsageRequestParams.builder()
                                                        .filterParams(ActiveServiceMonitoredFilterParams.builder()
                                                                          .serviceIdentifier("service2")
                                                                          .projectIdentifier("newProject")
                                                                          .build())
                                                        .pageRequest(Pageable.ofSize(10))
                                                        .build();
    Page<ActiveServiceMonitoredDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList = new ArrayList<>(result.toList());
    activeServiceMonitoredDTOList.sort(Comparator.comparing(ActiveServiceMonitoredDTO::getIdentifier));
    assertThat(activeServiceMonitoredDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceMonitoredDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  @Ignore(value = "Deprecated as of now")
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
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    MonitoredServiceDTO monitoredServiceDTO4 = createMonitoredServiceDTOBuilder("ms3", "service3", "env3").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO4);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms1", true);
    ProjectParams newParams = builderFactory.getProjectParams();
    newParams.setOrgIdentifier("newOrg");
    monitoredServiceService.setHealthMonitoringFlag(newParams, "ms12", true);
    monitoredServiceService.setHealthMonitoringFlag(builderFactory.getProjectParams(), "ms2", true);
    PageableUsageRequestParams usageRequestParams = DefaultPageableUsageRequestParams.builder()
                                                        .filterParams(ActiveServiceMonitoredFilterParams.builder()
                                                                          .serviceIdentifier("service2")
                                                                          .orgIdentifier("newOrg")
                                                                          .build())
                                                        .pageRequest(Pageable.ofSize(10))
                                                        .build();
    Page<ActiveServiceMonitoredDTO> result = licenseUsageInterface.listLicenseUsage(
        builderFactory.getContext().getAccountId(), ModuleType.SRM, 10l, usageRequestParams);
    assertThat(result.getTotalElements()).isEqualTo(1);
    List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList = new ArrayList<>(result.toList());
    activeServiceMonitoredDTOList.sort(Comparator.comparing(ActiveServiceMonitoredDTO::getIdentifier));
    assertThat(activeServiceMonitoredDTOList.get(0).getIdentifier()).isEqualTo("service2");
    assertThat(activeServiceMonitoredDTOList.get(0).getMonitoredServiceCount()).isEqualTo(1);
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

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder(
      String monitoredServiceIdentifier, String serviceIdentifier, String environmentIdentifier) {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name("test");
  }
}
