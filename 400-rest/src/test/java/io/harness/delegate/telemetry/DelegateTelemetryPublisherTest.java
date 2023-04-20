/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.telemetry;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateType;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateTelemetryPublisherTest extends WingsBaseTest {
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Mock private DelegateService delegateService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private AccountService accountService;
  private DelegateTelemetryPublisher delegateTelemetryPublisher;

  @Before
  public void setUp() {
    initMocks(this);
    delegateTelemetryPublisher = new DelegateTelemetryPublisher(
        persistenceIteratorFactory, persistenceProvider, delegateService, telemetryReporter, accountService);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testSendTelemetryTrackEvents_WhenInvokedBy_UserPrincipal() {
    String accountId = "123";
    String eventName = "test_event";
    String delegateType = DelegateType.KUBERNETES;
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("NG", true);
    properties.put("Type", delegateType);
    UserPrincipal userPrincipal = new UserPrincipal("testUser", "testEmail@xyz.com", "testUser", accountId);
    SourcePrincipalContextBuilder.setSourcePrincipal(userPrincipal);

    delegateTelemetryPublisher.sendTelemetryTrackEvents(accountId, delegateType, true, eventName);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventName, userPrincipal.getEmail(), accountId, properties, null,
            io.harness.telemetry.Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testSendTelemetryTrackEvents_WhenInvokedBy_ServiceAccountPrincipal() {
    String accountId = "123";
    String eventName = "test_event";
    String delegateType = DelegateType.KUBERNETES;
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("NG", true);
    properties.put("Type", delegateType);
    ServiceAccountPrincipal serviceAccountPrincipal =
        new ServiceAccountPrincipal("testUser", "testEmail@xyz.com", "testUser", accountId);
    SourcePrincipalContextBuilder.setSourcePrincipal(serviceAccountPrincipal);

    delegateTelemetryPublisher.sendTelemetryTrackEvents(accountId, delegateType, true, eventName);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventName, serviceAccountPrincipal.getEmail(), accountId, properties, null,
            io.harness.telemetry.Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testSendTelemetryTrackEvents_WhenInvokedBy_ServicePrincipal() {
    String accountId = "123";
    String eventName = "test_event";
    String delegateType = DelegateType.KUBERNETES;
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("NG", true);
    properties.put("Type", delegateType);
    ServicePrincipal serviceAccountPrincipal = new ServicePrincipal("testService");
    SourcePrincipalContextBuilder.setSourcePrincipal(serviceAccountPrincipal);

    delegateTelemetryPublisher.sendTelemetryTrackEvents(accountId, delegateType, true, eventName);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventName, "Harness-Analytics-123", accountId, properties, null,
            io.harness.telemetry.Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }
}
