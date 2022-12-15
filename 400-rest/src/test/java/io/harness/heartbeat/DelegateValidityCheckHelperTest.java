/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.heartbeat;

import static io.harness.delegate.message.ManagerMessageConstants.MIGRATE;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.delegate.utils.DelegateValidityCheckHelper;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateValidityCheckHelperTest extends WingsBaseTest {
  private static String DELEGATE_GROUP_ID = "test-group-id";
  private static String DELEGATE_DELETED_GROUP_ID = "test-group-deleted-id";

  @Mock private AccountService accountService;
  @Mock private LicenseService licenseService;
  @InjectMocks @Inject private DelegateValidityCheckHelper validityCheckHelper;
  @Inject private HPersistence persistence;

  @Before
  public void setup() {
    persistence.save(DelegateGroup.builder().uuid(DELEGATE_GROUP_ID).status(DelegateGroupStatus.ENABLED).build());
    persistence.save(
        DelegateGroup.builder().uuid(DELEGATE_DELETED_GROUP_ID).status(DelegateGroupStatus.DELETED).build());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void test_validityCheck_success() {
    final Delegate existingDelegate = Delegate.builder().delegateGroupId(DELEGATE_GROUP_ID).build();
    when(accountService.isAccountMigrated(any())).thenReturn(false);
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    assertThat(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(existingDelegate))
        .isEqualTo(Optional.empty());
    assertThat(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(Delegate.builder().build()))
        .isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void test_givenAccountDeleted_return_selfDestruct() {
    final Delegate existingDelegate = Delegate.builder().delegateGroupId(DELEGATE_GROUP_ID).build();
    when(accountService.isAccountMigrated(any())).thenReturn(false);
    when(licenseService.isAccountDeleted(any())).thenReturn(true);
    assertThat(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(existingDelegate))
        .isEqualTo(Optional.of(SELF_DESTRUCT));
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void test_givenAccountMigrated_return_selfDestruct() {
    final Delegate existingDelegate = Delegate.builder().delegateGroupId(DELEGATE_GROUP_ID).build();
    when(accountService.isAccountMigrated(any())).thenReturn(true);
    final Account mockAccount = mock(Account.class);
    final String testUrl = "test-url";
    when(mockAccount.getMigratedToClusterUrl()).thenReturn(testUrl);
    when(accountService.get(any())).thenReturn(mockAccount);
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    assertThat(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(existingDelegate))
        .isEqualTo(Optional.of(MIGRATE + testUrl));
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void test_givenGroupDeleted_return_selfDestruct() {
    final Delegate existingDelegate = Delegate.builder().delegateGroupId(DELEGATE_DELETED_GROUP_ID).build();
    when(accountService.isAccountMigrated(any())).thenReturn(false);
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    assertThat(validityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(existingDelegate))
        .isEqualTo(Optional.of(SELF_DESTRUCT));
  }
}
