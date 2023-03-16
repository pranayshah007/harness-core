/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.ACCOUNT1_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateDaoTest extends WingsBaseTest {
  private String delegateId = "DELEGATE_ID";
  private String delegateId2 = "DELEGATE_ID2";
  private String accountId = "ACCOUNT_ID";
  private static final String VERSION = "1.0.0";
  private Delegate delegate;

  @Inject private DelegateDao delegateDao;
  @Inject private DelegateService delegateService;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    delegate = Delegate.builder().uuid(delegateId).build();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateDisconnected() {
    String delegateId = persistence.save(createDelegateBuilder().build());
    delegateDao.delegateDisconnected(accountId, delegateId);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegateId);
    assertThat(updatedDelegate).isNotNull();
    assertThat(updatedDelegate.getUuid()).isEqualTo(delegateId);
    assertThat(updatedDelegate.isDisconnected()).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCheckAnyDelegateIsConnected() {
    String delegateId = persistence.save(createDelegateBuilder().disconnected(false).build());
    String delegateId2 = persistence.save(createDelegateBuilder()
                                              .lastHeartBeat(currentTimeMillis() - ofMinutes(1).toMillis())
                                              .disconnected(false)
                                              .build());
    String delegateId3 = persistence.save(createDelegateBuilder().disconnected(true).build());
    assertThat(delegateDao.checkAnyDelegateIsConnected(accountId, Arrays.asList(delegateId, delegateId2))).isTrue();
    assertThat(delegateDao.checkAnyDelegateIsConnected(accountId, Arrays.asList(delegateId, delegateId3))).isTrue();
    assertThat(delegateDao.checkAnyDelegateIsConnected(accountId, Arrays.asList("DELEGATE_ID3"))).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testNumberOfActiveDelegatesPerVersion() {
    persistence.save(createDelegateBuilder().build());
    persistence.save(createDelegateBuilder().build());
    assertThat(delegateDao.numberOfActiveDelegatesPerVersion(VERSION, null)).isEqualTo(2L);
    persistence.save(createDelegateBuilder().version("1.7").build());
    assertThat(delegateDao.numberOfActiveDelegatesPerVersion(VERSION, null)).isEqualTo(2L);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testObtainActiveDelegatesForAccount() {
    persistence.save(createDelegateBuilder().lastHeartBeat(System.currentTimeMillis()).build());
    persistence.save(createDelegateBuilder().disconnected(true).build());
    persistence.save(createDelegateBuilder().build());
    assertThat(delegateDao.obtainActiveDelegates(ACCOUNT_ID).entrySet().size()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testObtainActiveDelegatesForVersionGroupByAccount() {
    persistence.save(createDelegateBuilder().accountId(ACCOUNT1_ID).lastHeartBeat(System.currentTimeMillis()).build());
    persistence.save(createDelegateBuilder().accountId(ACCOUNT_ID).disconnected(true).build());
    persistence.save(createDelegateBuilder().accountId(ACCOUNT1_ID).build());
    persistence.save(createDelegateBuilder().accountId(ACCOUNT_ID).build());
    persistence.save(createDelegateBuilder()
                         .accountId(ACCOUNT_ID)
                         .lastHeartBeat(currentTimeMillis() - ofMinutes(9).toMillis())
                         .build());
    assertThat(delegateDao.obtainActiveDelegatesGroupByAccount(VERSION).entrySet().size()).isEqualTo(2L);
    assertThat(delegateDao.obtainActiveDelegatesGroupByAccount(VERSION).get(ACCOUNT_ID).size()).isEqualTo(1);
    assertThat(delegateDao.obtainActiveDelegatesGroupByAccount(VERSION).get(ACCOUNT1_ID).size()).isEqualTo(2);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
