/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.heartbeat;

import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateHeartbeatParams;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.heartbeat.polling.DelegatePollingHeartbeatService;
import io.harness.delegate.utils.DelegateJreVersionHelper;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.licensing.LicenseService;

import com.google.inject.Inject;
import java.time.Clock;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@RunWith(JUnit4.class)
public class DelegatePollingHeartbeatServiceTest extends WingsBaseTest {
  private static String TEST_DELEGATE_ID = "pysS0QJgSom03tGUxtz85A";
  private static String TEST_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static Boolean TEST_USECNDFORDELEGATESTORAGE = false;
  private static String TEST_TARGET_JRE_VERSION = "X.X.X";

  private static long LAST_HEARTBEAT = 1663555868450L;

  @Mock private Clock clock;
  @Mock private DelegateMetricsService delegateMetricsService;
  @Mock private LicenseService licenseService;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private DelegateJreVersionHelper delegateJreVersionHelper;
  @InjectMocks @Inject private DelegatePollingHeartbeatService delegatePollingHeartbeatService;
  @InjectMocks @Inject private DelegateCache delegateCache;

  @Inject private HPersistence persistence;

  @Before
  public void setup() {
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    when(delegateJreVersionHelper.getTargetJreVersion()).thenReturn(TEST_TARGET_JRE_VERSION);
    when(mainConfiguration.useCdnForDelegateStorage()).thenReturn(TEST_USECNDFORDELEGATESTORAGE);
    persistence.save(Delegate.builder()
                         .heartbeatAsObject(true)
                         .uuid(TEST_DELEGATE_ID)
                         .accountId(TEST_ACCOUNT_ID)
                         .lastHeartBeat(LAST_HEARTBEAT)
                         .build());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testPollingHeartbeat_success() {
    final long currentMillis = LAST_HEARTBEAT + 1000L * 60;
    when(clock.millis()).thenReturn(currentMillis);
    DelegateHeartbeatParams testHeartbeatRequestParam = DelegateParams.builder()
                                                            .delegateId(TEST_DELEGATE_ID)
                                                            .accountId(TEST_ACCOUNT_ID)
                                                            .lastHeartBeat(LAST_HEARTBEAT)
                                                            .build();
    DelegateHeartbeatResponse delegateHeartbeatResponse =
        delegatePollingHeartbeatService.process(testHeartbeatRequestParam);
    assertThat(delegateHeartbeatResponse)
        .isEqualTo(DelegateHeartbeatResponse.builder()
                       .status(DelegateInstanceStatus.ENABLED.toString())
                       .delegateId(TEST_DELEGATE_ID)
                       .useCdn(TEST_USECNDFORDELEGATESTORAGE)
                       .jreVersion(TEST_TARGET_JRE_VERSION)
                       .build());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testHeartbeat_givenDelegateNotFound_expectStatusDeleted() {
    final long currentMillis = LAST_HEARTBEAT + 1000L * 60;
    when(clock.millis()).thenReturn(currentMillis);
    DelegateHeartbeatParams testHeartbeatRequestParam = DelegateParams.builder()
                                                            .delegateId("not_existent")
                                                            .accountId(TEST_ACCOUNT_ID)
                                                            .lastHeartBeat(LAST_HEARTBEAT)
                                                            .build();
    DelegateHeartbeatResponse delegateHeartbeatResponse =
        delegatePollingHeartbeatService.process(testHeartbeatRequestParam);
    assertThat(delegateHeartbeatResponse)
        .isEqualTo(DelegateHeartbeatResponse.builder()
                       .delegateId("not_existent")
                       .jreVersion(TEST_TARGET_JRE_VERSION)
                       .status(DelegateInstanceStatus.DELETED.toString())
                       .build());
  }
}
