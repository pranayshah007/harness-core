/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.rule.OwnerRule.ARPITJ;
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
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.api.SRMTelemetrySentStatusService;
import io.harness.cvng.usage.impl.SRMLicenseUsageDTO;
import io.harness.cvng.usage.impl.SRMLicenseUsageImpl;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
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

@OwnedBy(CV)
public class SrmTelemetryPublisherTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private SRMLicenseUsageImpl licenseUsageInterface = mock(SRMLicenseUsageImpl.class);
  @Mock private TelemetryReporter telemetryReporter;

  @Mock private AccountUtils accountUtils;
  @Mock private SRMTelemetrySentStatusService srmTelemetrySentStatusService;
  @InjectMocks @Spy SrmTelemetryPublisher telemetryPublisher;

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    SRMLicenseUsageDTO srmLicenseUsageDTO =
        SRMLicenseUsageDTO.builder().activeServices(UsageDataDTO.builder().count(20L).build()).build();
    doReturn(srmLicenseUsageDTO)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.SRM), anyLong(), any());

    doReturn(true).when(srmTelemetrySentStatusService).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    AccountDTO accountDTO1 = AccountDTO.builder().identifier("acc1").build();
    AccountDTO accountDTO2 = AccountDTO.builder().identifier("acc2").build();
    List<AccountDTO> accountDTOList = new ArrayList<>();
    accountDTOList.add(accountDTO1);
    accountDTOList.add(accountDTO2);
    List<String> accountIdList = new ArrayList<>();
    accountIdList.add("acc1");
    accountIdList.add("acc2");
    doReturn(accountIdList).when(accountUtils).getAllAccountIds();

    HashMap<String, Object> firstAccountExpectedMap = new HashMap<>();
    firstAccountExpectedMap.put("group_type", "Account");
    firstAccountExpectedMap.put("group_id", "acc1");
    firstAccountExpectedMap.put("srm_license_services_monitored", srmLicenseUsageDTO.getActiveServices().getCount());

    HashMap<String, Object> secondAccountExpectedMap = new HashMap<>();
    secondAccountExpectedMap.put("group_type", "Account");
    secondAccountExpectedMap.put("group_id", "acc2");
    secondAccountExpectedMap.put("srm_license_services_monitored", srmLicenseUsageDTO.getActiveServices().getCount());

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc1", null, firstAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
    verify(telemetryReporter, times(1))
        .sendGroupEvent("acc2", null, secondAccountExpectedMap, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRecordSkipTelemetry() {
    SRMLicenseUsageDTO srmLicenseUsageDTO =
        SRMLicenseUsageDTO.builder().activeServices(UsageDataDTO.builder().count(20L).build()).build();
    doReturn(srmLicenseUsageDTO)
        .when(licenseUsageInterface)
        .getLicenseUsage(anyString(), eq(ModuleType.SRM), anyLong(), any());

    doReturn(false).when(srmTelemetrySentStatusService).updateTimestampIfOlderThan(anyString(), anyLong(), anyLong());
    AccountDTO accountDTO1 = AccountDTO.builder().identifier("acc1").build();
    AccountDTO accountDTO2 = AccountDTO.builder().identifier("acc2").build();
    List<AccountDTO> accountDTOList = new ArrayList<>();
    accountDTOList.add(accountDTO1);
    accountDTOList.add(accountDTO2);
    List<String> accountIdList = new ArrayList<>();
    accountIdList.add("acc1");
    accountIdList.add("acc2");
    doReturn(accountIdList).when(accountUtils).getAllAccountIds();

    telemetryPublisher.recordTelemetry();
    verify(telemetryReporter, times(0)).sendGroupEvent(anyString(), anyString(), any(), anyMap(), any());
  }
}
