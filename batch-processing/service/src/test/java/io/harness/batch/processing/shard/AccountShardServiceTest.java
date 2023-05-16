/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.shard;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.PodInfoConfig;
import io.harness.batch.processing.dao.intfc.AccountShardMappingDao;
import io.harness.batch.processing.entities.AccountShardMapping;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountShardServiceTest extends CategoryTest {
  @InjectMocks private AccountShardService accountShardService;
  @Mock private BatchMainConfig mainConfig;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private AccountShardMappingDao accountShardMappingDao;

  private final String POD_NAME = "batch-processing-0";
  private final String ISOLATED_POD_NAME = "batch-processing-2";
  private final int REPLICAS = 4;
  private final int ISOLATED_REPLICAS = 2;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetActiveAccounts() {
    PodInfoConfig podInfoConfig =
        PodInfoConfig.builder().name(POD_NAME).replica(REPLICAS).isolatedReplica(ISOLATED_REPLICAS).build();
    when(mainConfig.getPodInfoConfig()).thenReturn(podInfoConfig);
    ImmutableList<Account> accounts = ImmutableList.of(getAccount("accountId1"), getAccount("accountId2"),
        getAccount("accountId3"), getAccount("accountId4"), getAccount("accountId5"));
    when(cloudToHarnessMappingService.getCeEnabledAccounts()).thenReturn(accounts);
    when(accountShardMappingDao.getAccountShardMapping())
        .thenReturn(ImmutableList.of(AccountShardMapping.builder().accountId("accountId5").shardId(1).build()));
    List<AccountLicenseDTO> ceEnabledAccounts = accountShardService.getCeEnabledAccounts();
    assertThat(ceEnabledAccounts.stream().map(AccountLicenseDTO::getAccountIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("accountId2", "accountId4");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetIsolatedAccounts() {
    PodInfoConfig podInfoConfig =
        PodInfoConfig.builder().name(ISOLATED_POD_NAME).replica(REPLICAS).isolatedReplica(ISOLATED_REPLICAS).build();
    when(mainConfig.getPodInfoConfig()).thenReturn(podInfoConfig);
    ImmutableList<Account> accounts = ImmutableList.of(getAccount("accountId1"), getAccount("accountId2"),
        getAccount("accountId3"), getAccount("accountId4"), getAccount("accountId5"), getAccount("accountId6"));
    when(cloudToHarnessMappingService.getCeEnabledAccounts()).thenReturn(accounts);
    when(accountShardMappingDao.getAccountShardMapping())
        .thenReturn(ImmutableList.of(AccountShardMapping.builder().accountId("accountId5").shardId(1).build(),
            AccountShardMapping.builder().accountId("accountId6").shardId(1).build()));
    List<AccountLicenseDTO> ceEnabledAccounts = accountShardService.getCeEnabledAccounts();
    assertThat(ceEnabledAccounts.stream().map(AccountLicenseDTO::getAccountIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("accountId5", "accountId6");
  }

  private Account getAccount(String uuid) {
    return Account.Builder.anAccount().withUuid(uuid).build();
  }
}
