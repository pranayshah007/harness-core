/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.reporting;

import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.telemetry.Destination.ALL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.IDPLicenseUsageDTO;
import io.harness.idp.license.usage.repositories.IDPTelemetrySentStatusRepository;
import io.harness.idp.license.usage.service.impl.IDPLicenseUsageImpl;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPTelemetryPublisherTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  final List<String> allNGAccountIds = List.of(TEST_ACCOUNT_IDENTIFIER);

  AutoCloseable openMocks;
  @InjectMocks IDPTelemetryPublisher idpTelemetryPublisher;
  @Mock AccountUtils accountUtils;
  @Mock IDPTelemetrySentStatusRepository idpTelemetrySentStatusRepository;
  @Mock IDPLicenseUsageImpl idpLicenseUsage;
  @Mock TelemetryReporter telemetryReporter;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    IDPLicenseUsageDTO idpLicenseUsageDTO = buildIDPLicenseUsageDTO();

    HashMap<String, Object> map = new HashMap<>() {
      {
        put("group_type", "Account");
        put("group_id", TEST_ACCOUNT_IDENTIFIER);
        put("idp_license_active_developers", 100L);
      }
    };

    when(accountUtils.getAllNGAccountIds()).thenReturn(allNGAccountIds);
    when(idpTelemetrySentStatusRepository.updateTimestampIfOlderThan(eq(TEST_ACCOUNT_IDENTIFIER), anyLong(), anyLong()))
        .thenReturn(true);
    when(idpLicenseUsage.getLicenseUsage(TEST_ACCOUNT_IDENTIFIER, ModuleType.IDP, new Date().getTime(), null))
        .thenReturn(idpLicenseUsageDTO);
    doNothing()
        .when(telemetryReporter)
        .sendGroupEvent(TEST_ACCOUNT_IDENTIFIER, null, map, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(false).build());

    idpTelemetryPublisher.recordTelemetry();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testRecordTelemetryWithException() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(allNGAccountIds);
    when(idpTelemetrySentStatusRepository.updateTimestampIfOlderThan(eq(TEST_ACCOUNT_IDENTIFIER), anyLong(), anyLong()))
        .thenReturn(true);
    given(idpLicenseUsage.getLicenseUsage(
              eq(TEST_ACCOUNT_IDENTIFIER), any(ModuleType.class), anyLong(), any(UsageRequestParams.class)))
        .willAnswer(invocation -> { throw new Exception("Exception Throw"); });

    idpTelemetryPublisher.recordTelemetry();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private IDPLicenseUsageDTO buildIDPLicenseUsageDTO() {
    return IDPLicenseUsageDTO.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .activeDevelopers(UsageDataDTO.builder().count(100).build())
        .build();
  }
}
