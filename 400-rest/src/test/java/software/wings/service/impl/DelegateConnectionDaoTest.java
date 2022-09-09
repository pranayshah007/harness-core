/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUPAM;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ConnectionMode;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateConnectionDetails;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.DelegateConnection;
import software.wings.beans.DelegateConnection.DelegateConnectionKeys;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateConnectionDaoTest extends WingsBaseTest {
  private String delegateId = "DELEGATE_ID";
  private String delegateId2 = "DELEGATE_ID2";
  private String accountId = "ACCOUNT_ID";
  private static final String VERSION = "1.0.0";
  private Delegate delegate;

  @Inject private DelegateConnectionDetailsHelper delegateConnectionDetailsHelper;
  @Inject private DelegateService delegateService;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    delegate = Delegate.builder().uuid(delegateId).build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldReturnNullWhenList() {
    List<DelegateConnectionDetails> delegateConnections =
        delegateConnectionDetailsHelper.list(delegate.getUuid());
    assertThat(delegateConnections).isEqualTo(emptyList());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDoConnectionHeartbeat() {
    delegateService.registerHeartbeat(ACCOUNT_ID, DELEGATE_ID,
        DelegateConnectionHeartbeat.builder().delegateConnectionId(generateUuid()).version("1.0.1").build(),
        ConnectionMode.POLLING);
    DelegateConnection connection =
        persistence.createQuery(DelegateConnection.class).filter(DelegateConnectionKeys.accountId, ACCOUNT_ID).get();
    assertThat(connection.getVersion()).isEqualTo("1.0.1");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldObtainActiveDelegateConnections() {
    delegateService.registerHeartbeat(ACCOUNT_ID, delegateId,
        DelegateConnectionHeartbeat.builder().delegateConnectionId(generateUuid()).version("1.0.1").build(),
        ConnectionMode.POLLING);
    delegateService.registerHeartbeat(ACCOUNT_ID, delegateId,
        DelegateConnectionHeartbeat.builder().delegateConnectionId(generateUuid()).version("1.0.2").build(),
        ConnectionMode.POLLING);
    delegateService.registerHeartbeat(ACCOUNT_ID, delegateId2,
        DelegateConnectionHeartbeat.builder().delegateConnectionId(generateUuid()).version("1.0.1").build(),
        ConnectionMode.POLLING);

    Map<String, List<DelegateConnectionDetails>> delegateConnections =
        delegateConnectionDetailsHelper.obtainActiveDelegateConnections(ACCOUNT_ID);

    assertThat(delegateConnections).hasSize(2);
    assertThat(delegateConnections.get(delegateId)).hasSize(2);
    assertThat(delegateConnections.get(delegateId2)).hasSize(1);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDelegateDisconnected() {
    String delegateId = generateUuid();
    String delegateConnectionId = generateUuid();

    DelegateConnection delegateConnection = DelegateConnection.builder()
                                                .accountId(accountId)
                                                .uuid(delegateConnectionId)
                                                .delegateId(delegateId)
                                                .disconnected(false)
                                                .build();

    persistence.save(delegateConnection);

    delegateConnectionDetailsHelper.delegateDisconnected(delegateId);

    DelegateConnection retrievedDelegateConnection =
        persistence.createQuery(DelegateConnection.class)
            .filter(DelegateConnectionKeys.uuid, delegateConnection.getUuid())
            .get();

    assertThat(retrievedDelegateConnection).isNotNull();
    assertThat(retrievedDelegateConnection.getDelegateId()).isEqualTo(delegateId);
    assertThat(retrievedDelegateConnection.getAccountId()).isEqualTo(accountId);
    assertThat(retrievedDelegateConnection.isDisconnected()).isTrue();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testCheckAnyDelegateIsConnected() {
    String delegateConnectionId1 = generateUuid();

    persistence.save(DelegateConnection.builder()
                         .accountId(accountId)
                         .uuid(delegateConnectionId1)
                         .delegateId(delegateId)
                         .disconnected(false)
                         .lastHeartbeat(System.currentTimeMillis())
                         .build());

    assertThat(
        delegateConnectionDetailsHelper.checkAnyDelegateIsConnected(
            accountId, Arrays.asList(delegateId, delegateId2)))
        .isTrue();
    assertThat(
        delegateConnectionDetailsHelper.checkAnyDelegateIsConnected(
            accountId, Arrays.asList(delegateId2, "DELEGATE_ID3")))
        .isFalse();
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testNumberOfActiveDelegateConnectionsPerVersionWithNoAccountID() {
    persistence.save(DelegateConnection.builder()
                         .accountId(accountId)
                         .uuid(generateUuid())
                         .delegateId(delegateId)
                         .disconnected(false)
                         .lastHeartbeat(System.currentTimeMillis())
                         .version(VERSION)
                         .build());

    assertThat(
        delegateConnectionDetailsHelper.numberOfActiveDelegateConnectionsPerVersion(VERSION, null)).isEqualTo(1L);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testNumberOfActiveDelegateConnectionsPerVersion() {
    persistence.save(DelegateConnection.builder()
                         .accountId(accountId)
                         .uuid(generateUuid())
                         .delegateId(delegateId)
                         .disconnected(false)
                         .lastHeartbeat(System.currentTimeMillis())
                         .version(VERSION)
                         .build());

    assertThat(delegateConnectionDetailsHelper.numberOfActiveDelegateConnectionsPerVersion(
        VERSION, accountId)).isEqualTo(1L);
  }
}
