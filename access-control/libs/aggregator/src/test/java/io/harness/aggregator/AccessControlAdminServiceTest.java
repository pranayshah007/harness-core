/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.aggregator.models.BlockedAccount;
import io.harness.aggregator.repositories.BlockedEntityRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PL)
public class AccessControlAdminServiceTest {
  @Mock private BlockedEntityRepository blockedEntityRepository;
  @Mock private GenerateACLsFromRoleAssignmentsJob generateACLsFromRoleAssignmentsJob;
  private AccessControlAdminService accessControlAdminService;
  private String accountIdentifier = "accountIdentifier-1";
  private LoadingCache<String, Boolean> blockedAccountCache;
  private Field blockedAccountCacheField;

  @Before
  public void setup() throws NoSuchFieldException, IllegalAccessException {
    when(blockedEntityRepository.find(anyString()))
        .thenReturn(Optional.of(BlockedAccount.builder().accountIdentifier(accountIdentifier).build()));
    when(blockedEntityRepository.findAll())
        .thenReturn(List.of(BlockedAccount.builder().accountIdentifier(accountIdentifier).build()));
    accessControlAdminService =
        new AccessControlAdminService(blockedEntityRepository, generateACLsFromRoleAssignmentsJob);

    blockedAccountCache = Caffeine.newBuilder()
                              .maximumSize(10000)
                              .expireAfterWrite(30, TimeUnit.MINUTES)
                              .build(accountId -> blockedEntityRepository.find(accountId).isPresent());
    blockedAccountCacheField = accessControlAdminService.getClass().getDeclaredField("blockedAccountCache");
    blockedAccountCacheField.setAccessible(true);
    blockedAccountCacheField.set(accessControlAdminService, blockedAccountCache);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testBlockAccount() {
    when(blockedEntityRepository.find(anyString())).thenReturn(Optional.empty());
    accessControlAdminService.block(accountIdentifier);
    verify(blockedEntityRepository, times(1)).create(any(BlockedAccount.class));

    Boolean blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testBlockAccountForAlreadyBlockedAccount() {
    blockedAccountCache.put(accountIdentifier, true);

    accessControlAdminService.block(accountIdentifier);

    verify(blockedEntityRepository, times(0)).create(any(BlockedAccount.class));

    Boolean blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testBlockAccountWhereItIsPresentInDBButNotInCache() {
    blockedAccountCache.put(accountIdentifier, false);

    accessControlAdminService.block(accountIdentifier);

    verify(blockedEntityRepository, times(0)).create(any(BlockedAccount.class));

    Boolean blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUnblockAccount() {
    when(blockedEntityRepository.find(anyString())).thenReturn(Optional.empty());
    blockedAccountCache.put(accountIdentifier, true);
    accessControlAdminService.unblock(accountIdentifier);
    verify(blockedEntityRepository, times(1)).delete(accountIdentifier);

    Boolean blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUnblockForNonBlockedAccount() {
    blockedAccountCache.put(accountIdentifier, false);

    when(blockedEntityRepository.find(anyString())).thenReturn(Optional.empty());
    accessControlAdminService.unblock(accountIdentifier);
    Boolean blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isFalse();

    accessControlAdminService.unblock(accountIdentifier);
    verify(blockedEntityRepository, times(2)).delete(accountIdentifier);

    blocked = blockedAccountCache.get(accountIdentifier);
    assertThat(blocked).isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetBlockedAccount() {
    when(blockedEntityRepository.find(anyString())).thenReturn(Optional.empty());

    boolean blocked = accessControlAdminService.isBlocked(accountIdentifier);

    assertThat(blocked).isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetNonBlockedAccount() {
    boolean blocked = accessControlAdminService.isBlocked(accountIdentifier);

    assertThat(blocked).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetBlockedAccountWithNullValue() {
    boolean blocked = accessControlAdminService.isBlocked(null);

    assertThat(blocked).isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAllBlockedAccounts() {
    List<BlockedAccount> allBlockedAccounts = accessControlAdminService.getAllBlockedAccounts();

    assertThat(allBlockedAccounts).isNotEmpty();
    assertThat(allBlockedAccounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetIfThereIsNoBlockedAccount() {
    when(blockedEntityRepository.findAll()).thenReturn(emptyList());
    List<BlockedAccount> allBlockedAccounts = accessControlAdminService.getAllBlockedAccounts();

    assertThat(allBlockedAccounts).isEmpty();
  }
}