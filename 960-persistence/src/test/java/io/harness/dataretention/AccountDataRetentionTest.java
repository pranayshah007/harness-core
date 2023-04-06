/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class AccountDataRetentionTest extends PersistenceTestBase {
  @Inject io.harness.dataretention.AccountDataRetentionService accountDataRetentionService;
  @Inject HPersistence persistence;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAccountDataRetentionUpdate() {
    String accountId = generateUuid();
    Long dataRetention = ofDays(1).toMillis();
    Map<String, Long> accounts = ImmutableMap.<String, Long>builder().put(accountId, dataRetention).build();

    Date date = new Date();
    long now = date.getTime();

    List<io.harness.dataretention.AccountDataRetentionTestEntity> accountDataRetentionTestEntities =
        ImmutableList.<io.harness.dataretention.AccountDataRetentionTestEntity>builder()
            .add(io.harness.dataretention.AccountDataRetentionTestEntity.builder()
                     .uuid("entity-1")
                     .accountId(accountId)
                     .createdAt(0)
                     .validUntil(null)
                     .build())
            .add(io.harness.dataretention.AccountDataRetentionTestEntity.builder()
                     .uuid("entity-2")
                     .accountId(accountId)
                     .createdAt(now)
                     .validUntil(null)
                     .build())
            .add(io.harness.dataretention.AccountDataRetentionTestEntity.builder()
                     .uuid("entity-3")
                     .accountId(accountId)
                     .createdAt(now)
                     .validUntil(date)
                     .build())
            .add(io.harness.dataretention.AccountDataRetentionTestEntity.builder()
                     .uuid("ready-entity-1")
                     .accountId(accountId)
                     .createdAt(now)
                     .validUntil(new Date(date.toInstant().plusMillis(dataRetention).toEpochMilli()))
                     .build())
            .build();

    accountDataRetentionTestEntities.forEach(entity -> persistence.save(entity));

    assertThat(accountDataRetentionService.correctValidUntilAccount(
                   io.harness.dataretention.AccountDataRetentionTestEntity.class, accounts, now, now + 1000))
        .isEqualTo(3);

    assertThat(accountDataRetentionService.correctValidUntilAccount(
                   io.harness.dataretention.AccountDataRetentionTestEntity.class, accounts, now, now + 1000))
        .isEqualTo(0);

    List<io.harness.dataretention.AccountDataRetentionTestEntity> updatedAccountDataRetentionTestEntities =
        persistence.createQuery(io.harness.dataretention.AccountDataRetentionTestEntity.class).asList();
    updatedAccountDataRetentionTestEntities.forEach(entity -> {
      assertThat(entity.getValidUntil().getTime()).as(entity.getUuid()).isEqualTo(now + dataRetention);
    });

    // Adding two minutes to the set data retention
    Map<String, Long> newAccounts = ImmutableMap.<String, Long>builder().put(accountId, dataRetention + 120000).build();
    assertThat(accountDataRetentionService.correctValidUntilAccount(
                   io.harness.dataretention.AccountDataRetentionTestEntity.class, newAccounts, now, now + 1000))
        .isEqualTo(0);

    assertThat(accountDataRetentionService.correctValidUntilAccount(
                   io.harness.dataretention.AccountDataRetentionTestEntity.class, newAccounts, now,
                   now + dataRetention + 1000))
        .isEqualTo(4);
  }
}
