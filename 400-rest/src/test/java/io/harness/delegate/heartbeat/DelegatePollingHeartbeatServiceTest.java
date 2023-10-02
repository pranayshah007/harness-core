/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

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
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.DuplicateDelegateException;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
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
  private static String TEST_CONNECTION_ID = generateTimeBasedUuid();
  private static String TEST_CONNECTION_ID1 = generateTimeBasedUuid();
  private static final String VERSION = "1.0.0";
  private static long LAST_HEARTBEAT = 1663555868450L;
  private static String TEST_LOCATION = "//loc";
  private static String TEST_LOCATION1 = "//loc1";
  private static String CONNECTION_ID = "CtiTQpogEe2zvtHHSW9Baw";

  @Mock private Clock clock;
  @Mock private DelegateMetricsService delegateMetricsService;
  @Mock private LicenseService licenseService;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private DelegateJreVersionHelper delegateJreVersionHelper;
  @InjectMocks @Inject private DelegatePollingHeartbeatService delegatePollingHeartbeatService;
  @InjectMocks @Inject private DelegateCache delegateCache;

  @Inject private HPersistence persistence;
  @Rule public ExpectedException thrown = ExpectedException.none();

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

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHeartBeatProcess_withExtendedParams() {
    String delegateId = persistence.save(createDelegateBuilder().build());
    // HB update 1
    DelegateHeartbeatParams params1 = DelegateParams.builder()
                                          .delegateId(delegateId)
                                          .delegateConnectionId(TEST_CONNECTION_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .location(TEST_LOCATION)
                                          .version(VERSION)
                                          .lastHeartBeat(System.currentTimeMillis())
                                          .build();
    delegatePollingHeartbeatService.process(params1);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegateId);
    assertThat(updatedDelegate.getLocation()).isEqualTo(TEST_LOCATION);
    assertThat(updatedDelegate.getDelegateConnectionId()).isEqualTo(TEST_CONNECTION_ID);
    assertThat(updatedDelegate.getVersion()).isEqualTo(VERSION);
    // HB update 2, with new connection id
    DelegateHeartbeatParams params2 = DelegateParams.builder()
                                          .delegateId(delegateId)
                                          .delegateConnectionId(TEST_CONNECTION_ID1)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .lastHeartBeat(System.currentTimeMillis())
                                          .build();
    delegatePollingHeartbeatService.process(params2);
    Delegate updatedDelegateWithNewConnectionId = persistence.get(Delegate.class, delegateId);
    assertThat(updatedDelegateWithNewConnectionId.getDelegateConnectionId()).isEqualTo(TEST_CONNECTION_ID1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHeartBeatRegister_withShellScriptSameLocation() {
    String delegateId = persistence.save(createDelegateBuilder().delegateType(DelegateType.SHELL_SCRIPT).build());
    // HB from location
    DelegateHeartbeatParams params1 = DelegateParams.builder()
                                          .delegateId(delegateId)
                                          .delegateConnectionId(CONNECTION_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .location(TEST_LOCATION)
                                          .version(VERSION)
                                          .lastHeartBeat(System.currentTimeMillis())
                                          .build();
    delegatePollingHeartbeatService.process(params1);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegateId);
    assertThat(updatedDelegate.getLocation()).isEqualTo(TEST_LOCATION);
    assertThat(updatedDelegate.getDelegateConnectionId()).isEqualTo(CONNECTION_ID);
    // HB from same connection id different location as current
    DelegateHeartbeatParams params2 = DelegateParams.builder()
                                          .delegateId(delegateId)
                                          .delegateConnectionId(CONNECTION_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .location(TEST_LOCATION1)
                                          .version(VERSION)
                                          .lastHeartBeat(System.currentTimeMillis())
                                          .build();
    Delegate updatedDelegate1 = persistence.get(Delegate.class, delegateId);
    thrown.expect(DuplicateDelegateException.class);
    delegatePollingHeartbeatService.shellScriptDelegateLocationCheck(updatedDelegate1, params2);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("delegateName")
        .polllingModeEnabled(true)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
