/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.telemetry.Destination.ALL;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;
import io.harness.cdlicense.impl.CgCdLicenseUsageService;
import io.harness.cdng.usage.impl.CDLicenseUsageImpl;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.ng.core.dto.AccountDTO;
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
  @Mock private AccountClient accountClient;
  @Mock private CdTelemetryStatusRepository cdTelemetryStatusRepository;
  @Mock private CgCdLicenseUsageService cgLicenseUsageService;

  @InjectMocks @Spy CdTelemetryPublisher telemetryPublisher;
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    CDLicenseUsageDTO cdLicenseUsage = CDLicenseUsageDTO.builder()
                                           .activeServices(UsageDataDTO.builder().count(20L).build())
                                           .activeServiceInstances(UsageDataDTO.builder().count(30L).build())
                                           .build();
    doReturn(cdLicenseUsage)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.CD), anyLong(),
            eq(CDUsageRequestParams.builder().cdLicenseType(SERVICES).build()));

    CgActiveServicesUsageInfo cgLicenseUsage =
        CgActiveServicesUsageInfo.builder()
            .servicesConsumed(2L)
            .serviceLicenseConsumed(3L)
            .activeServiceUsage(Arrays.asList(
                new CgServiceUsage("service1", "svcId1", 1, 9), new CgServiceUsage("service2", "svcId2", 2, 22)))
            .build();
    doReturn(cgLicenseUsage).when(cgLicenseUsageService).getActiveServiceLicenseUsage(anyString());
    doReturn(true).when(cdTelemetryStatusRepository).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    AccountDTO accountDTO1 = AccountDTO.builder().identifier("acc1").build();
    AccountDTO accountDTO2 = AccountDTO.builder().identifier("acc2").build();
    List<AccountDTO> accountDTOList = new ArrayList<>();
    accountDTOList.add(accountDTO1);
    accountDTOList.add(accountDTO2);
    doReturn(accountDTOList).when(telemetryPublisher).getAllAccounts();
    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("cd_license_services_used", cdLicenseUsage.getActiveServices().getCount());
    firstAccountExpectedMap.put(
        "cd_license_service_instances_used", cdLicenseUsage.getActiveServiceInstances().getCount());
    firstAccountExpectedMap.put("cd_license_cg_services_used", 2L);
    firstAccountExpectedMap.put("cd_license_cg_service_instances_used", 31L);
    firstAccountExpectedMap.put("account_deploy_type", null);

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("cd_license_services_used", cdLicenseUsage.getActiveServices().getCount());
    secondAccountExpectedMap.put(
        "cd_license_service_instances_used", cdLicenseUsage.getActiveServiceInstances().getCount());
    secondAccountExpectedMap.put("cd_license_cg_services_used", 2L);
    secondAccountExpectedMap.put("cd_license_cg_service_instances_used", 31L);
    secondAccountExpectedMap.put("account_deploy_type", null);

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
    AccountDTO accountDTO1 = AccountDTO.builder().identifier("acc1").build();
    AccountDTO accountDTO2 = AccountDTO.builder().identifier("acc2").build();
    List<AccountDTO> accountDTOList = new ArrayList<>();
    accountDTOList.add(accountDTO1);
    accountDTOList.add(accountDTO2);
    doReturn(accountDTOList).when(telemetryPublisher).getAllAccounts();

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(0)).sendGroupEvent(anyString(), anyString(), anyObject(), anyMap(), anyObject());
  }
}
