/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.cd.CDLicenseType.SERVICE_INSTANCES;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.telemetry.Destination.ALL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.AccountConfig;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;
import io.harness.cdng.usage.impl.CDLicenseUsageImpl;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.repositories.telemetry.CdTelemetryStatusRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class CdTelemetryPublisherTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private CDLicenseUsageImpl licenseUsageInterface = mock(CDLicenseUsageImpl.class);
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private AccountUtils accountUtils;
  @Mock private CdTelemetryStatusRepository cdTelemetryStatusRepository;
  @Mock private AccountConfig accountConfig;

  @InjectMocks @Spy CdTelemetryPublisher telemetryPublisher;
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    ServiceUsageDTO cdServiceLicenseUsage = ServiceUsageDTO.builder()
                                                .activeServices(UsageDataDTO.builder().count(20L).build())
                                                .serviceLicenses(UsageDataDTO.builder().count(25L).build())
                                                .activeServiceInstances(UsageDataDTO.builder().count(30L).build())
                                                .build();
    doReturn(cdServiceLicenseUsage)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.CD), anyLong(),
            eq(CDUsageRequestParams.builder().cdLicenseType(SERVICES).build()));

    ServiceInstanceUsageDTO serviceInstancesLicenseUsage =
        ServiceInstanceUsageDTO.builder()
            .activeServices(UsageDataDTO.builder().count(20L).build())
            .activeServiceInstances(UsageDataDTO.builder().count(30L).build())
            .build();
    doReturn(serviceInstancesLicenseUsage)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.CD), anyLong(),
            eq(CDUsageRequestParams.builder().cdLicenseType(SERVICE_INSTANCES).build()));

    doReturn(true).when(cdTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    List<String> accountIdList = new ArrayList<>();
    accountIdList.add("acc1");
    accountIdList.add("acc2");
    doReturn(accountIdList).when(accountUtils).getAllAccountIds();
    doReturn("someCluster").when(accountConfig).getDeploymentClusterName();

    CgActiveServicesUsageInfo cgLicenseUsage =
        CgActiveServicesUsageInfo.builder()
            .servicesConsumed(2L)
            .serviceLicenseConsumed(3L)
            .activeServiceUsage(Arrays.asList(new CgServiceUsage("service1", "svcId1", 1, 9, "appId1", "appName1"),
                new CgServiceUsage("service2", "svcId2", 2, 22, "appId1", "appName1")))
            .build();
    doReturn(cgLicenseUsage).when(telemetryPublisher).getCgLicenseUsageInfo("acc1");
    doReturn(cgLicenseUsage).when(telemetryPublisher).getCgLicenseUsageInfo("acc2");
    doReturn(33L).when(telemetryPublisher).getCgServiceInstancesUsageInfo("acc1");
    doReturn(43L).when(telemetryPublisher).getCgServiceInstancesUsageInfo("acc2");

    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("cd_license_services_used", cdServiceLicenseUsage.getServiceLicenses().getCount());
    firstAccountExpectedMap.put(
        "cd_license_service_instances_used", serviceInstancesLicenseUsage.getActiveServiceInstances().getCount());
    firstAccountExpectedMap.put("account_deploy_type", null);
    firstAccountExpectedMap.put("cd_license_cg_services_used", 3L);
    firstAccountExpectedMap.put("cd_license_cg_service_instances_used", 33L);
    firstAccountExpectedMap.put("harness_cluster_id", "someCluster");

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("cd_license_services_used", cdServiceLicenseUsage.getServiceLicenses().getCount());
    secondAccountExpectedMap.put(
        "cd_license_service_instances_used", serviceInstancesLicenseUsage.getActiveServiceInstances().getCount());
    secondAccountExpectedMap.put("account_deploy_type", null);
    secondAccountExpectedMap.put("cd_license_cg_services_used", 3L);
    secondAccountExpectedMap.put("cd_license_cg_service_instances_used", 43L);
    secondAccountExpectedMap.put("harness_cluster_id", "someCluster");

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc1", null, firstAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc2", null, secondAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testRecordSkipTelemetry() {
    CDLicenseUsageDTO cdLicenseUsage = CDLicenseUsageDTO.builder()
                                           .activeServices(UsageDataDTO.builder().count(20L).build())
                                           .activeServiceInstances(UsageDataDTO.builder().count(30L).build())
                                           .build();
    doReturn(cdLicenseUsage)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.CD), anyLong(),
            eq(CDUsageRequestParams.builder().cdLicenseType(SERVICES).build()));
    doReturn(false).when(cdTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    List<String> accountIdList = new ArrayList<>();
    accountIdList.add("acc1");
    accountIdList.add("acc2");
    doReturn(accountIdList).when(accountUtils).getAllAccountIds();

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(0)).sendGroupEvent(anyString(), anyString(), any(), anyMap(), any());
  }
}
