/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineTelemetryHelperTest extends CategoryTest {
  @Mock TelemetryReporter telemetryReporter;
  @Mock AccountService accountService;
  @InjectMocks PipelineTelemetryHelper pipelineTelemetryHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendTelemetryEventInternal() {
    ArgumentCaptor<HashMap> propertiesCaptor = ArgumentCaptor.forClass(HashMap.class);
    ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(AccountDTO.builder().name("accountName").build()).when(accountService).getAccount("accountId");
    pipelineTelemetryHelper.sendTelemetryEventInternal("eventName", "accountId", new HashMap<>());

    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventNameCaptor.capture(), any(), accountIdCaptor.capture(), propertiesCaptor.capture(), any(),
            any(), any());

    assertEquals(eventNameCaptor.getValue(), "eventName");
    assertEquals(accountIdCaptor.getValue(), "accountId");
    HashMap<String, Object> properties = propertiesCaptor.getValue();
    assertEquals(properties.get(ACCOUNT_NAME), "accountName");
  }
}
