/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.data.structure.UUIDGenerator.generateTimeBasedUuid;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateHeartbeatParams;
import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.heartbeat.stream.DelegateHeartbeatResponseStreamingWrapper;
import io.harness.delegate.heartbeat.stream.DelegateStreamHeartbeatService;
import io.harness.delegate.utils.DelegateValidityCheckHelper;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Optional;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
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
public class DelegateStreamHeartbeatServiceTest extends WingsBaseTest {
  private static String TEST_DELEGATE_ID = "pysS0QJgSom03tGUxtz85A";
  private static String TEST_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static String TEST_LOCATION = "//loc";
  private static String TEST_LOCATION1 = "//loc1";
  private static String CONNECTION_ID = "CtiTQpogEe2zvtHHSW9Baw";
  private static final String TEST_CONNECTION_ID = generateTimeBasedUuid();
  private static final String TEST_CONNECTION_ID1 = generateTimeBasedUuid();

  private static String TEST_STRING_HEARTBEAT_DELEGATE_ID = "-HibrDPmRSqs4kMee-LQVw";
  private static long LAST_HEARTBEAT = 1663555868450L;
  private static final String VERSION = "1.0.0";

  @Mock private Clock clock;
  @Mock private DelegateValidityCheckHelper validityCheckHelper;
  @Mock private DelegateMetricsService delegateMetricsService;
  @Mock private BroadcasterFactory broadcasterFactory;
  @InjectMocks @Inject private DelegateStreamHeartbeatService delegateStreamHeartbeatService;
  @InjectMocks @Inject private DelegateCache delegateCache;

  @Inject private HPersistence persistence;

  @Before
  public void setup() {
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(mock(Broadcaster.class));
    persistence.save(Delegate.builder()
                         .heartbeatAsObject(true)
                         .uuid(TEST_DELEGATE_ID)
                         .accountId(TEST_ACCOUNT_ID)
                         .lastHeartBeat(LAST_HEARTBEAT)
                         .build());
    persistence.save(Delegate.builder()
                         .heartbeatAsObject(false)
                         .uuid(TEST_STRING_HEARTBEAT_DELEGATE_ID)
                         .accountId(TEST_ACCOUNT_ID)
                         .lastHeartBeat(LAST_HEARTBEAT)
                         .build());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testHeartbeat_success() {
    final long currentMillis = LAST_HEARTBEAT + 1000L * 60;
    when(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(any())).thenReturn(Optional.empty());
    when(clock.millis()).thenReturn(currentMillis);
    DelegateHeartbeatParams testHeartbeatRequestParam = DelegateParams.builder()
                                                            .delegateId(TEST_DELEGATE_ID)
                                                            .accountId(TEST_ACCOUNT_ID)
                                                            .lastHeartBeat(LAST_HEARTBEAT)
                                                            .build();
    DelegateHeartbeatResponseStreamingWrapper responseStreamingWrapperAsObject =
        delegateStreamHeartbeatService.process(testHeartbeatRequestParam);
    assertThat(responseStreamingWrapperAsObject.get())
        .isEqualTo(DelegateHeartbeatResponseStreaming.builder()
                       .delegateId(TEST_DELEGATE_ID)
                       .responseSentAt(currentMillis)
                       .build());

    testHeartbeatRequestParam = DelegateParams.builder()
                                    .delegateId(TEST_STRING_HEARTBEAT_DELEGATE_ID)
                                    .accountId(TEST_ACCOUNT_ID)
                                    .lastHeartBeat(LAST_HEARTBEAT)
                                    .build();
    DelegateHeartbeatResponseStreamingWrapper responseStreamingWrapperAsString =
        delegateStreamHeartbeatService.process(testHeartbeatRequestParam);
    assertThat(responseStreamingWrapperAsString.get())
        .isEqualTo(new StringBuilder(128).append("[X]").append(TEST_STRING_HEARTBEAT_DELEGATE_ID).toString());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testHeartbeat_givenValidityCheckFail_expectSelfDestruct() {
    final long currentMillis = LAST_HEARTBEAT + 1000L * 60;
    when(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(any()))
        .thenReturn(Optional.of(SELF_DESTRUCT));
    when(clock.millis()).thenReturn(currentMillis);
    DelegateHeartbeatParams testHeartbeatRequestParam = DelegateParams.builder()
                                                            .delegateId(TEST_DELEGATE_ID)
                                                            .accountId(TEST_ACCOUNT_ID)
                                                            .lastHeartBeat(LAST_HEARTBEAT)
                                                            .build();
    DelegateHeartbeatResponseStreamingWrapper responseStreamingWrapperAsObject =
        delegateStreamHeartbeatService.process(testHeartbeatRequestParam);
    assertThat(responseStreamingWrapperAsObject.get()).isEqualTo(SELF_DESTRUCT);
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
    delegateStreamHeartbeatService.process(params1);
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
    delegateStreamHeartbeatService.process(params2);
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
    delegateStreamHeartbeatService.process(params1);
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
    assertThat(delegateStreamHeartbeatService.shellScriptDelegateLocationCheck(updatedDelegate1, params2)).isTrue();
    delegateStreamHeartbeatService.process(params2);
    Delegate updatedDelegate2 = persistence.get(Delegate.class, delegateId);
    assertThat(updatedDelegate2.getLocation()).isEqualTo(TEST_LOCATION1);
    // HB from same connection id same location as current
    DelegateHeartbeatParams params3 = DelegateParams.builder()
                                          .delegateId(delegateId)
                                          .delegateConnectionId(CONNECTION_ID)
                                          .accountId(TEST_ACCOUNT_ID)
                                          .location(TEST_LOCATION1)
                                          .version(VERSION)
                                          .lastHeartBeat(System.currentTimeMillis())
                                          .build();
    delegateStreamHeartbeatService.process(params3);
    Delegate updatedDelegate3 = persistence.get(Delegate.class, delegateId);
    assertThat(delegateStreamHeartbeatService.shellScriptDelegateLocationCheck(updatedDelegate3, params3)).isFalse();
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("delegateName")
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
