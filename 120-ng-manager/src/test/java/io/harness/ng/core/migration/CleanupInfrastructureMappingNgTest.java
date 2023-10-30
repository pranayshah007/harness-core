/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.rule.OwnerRule.BUHA;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.utils.AccountUtils;
import io.harness.category.element.UnitTests;
import io.harness.entities.InfrastructureMapping;
import io.harness.ng.core.migration.background.CleanupInfrastructureMappingNg;
import io.harness.repositories.infrastructuremapping.InfrastructureMappingRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

public class CleanupInfrastructureMappingNgTest extends NgManagerTestBase {
  @Mock private AccountUtils accountUtils;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private InfrastructureMappingRepository repository;
  @InjectMocks CleanupInfrastructureMappingNg cleanupInfrastructureMappingNg;

  String accountId1 = "accountId1";
  String accountId2 = "accountId2";
  String accountId3 = "accountId3";
  String deletedAccount1 = "deletedAccount1";
  String deletedAccount2 = "deletedAccount2";
  ArrayList<String> accountIds = new ArrayList<>(Arrays.asList(accountId1, accountId2, accountId3));

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCleanupMigration() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(accountIds);
    when(mongoTemplate.stream(
             new Query(new Criteria()).limit(NO_LIMIT).cursorBatchSize(10000), InfrastructureMapping.class))
        .thenReturn(createCloseableIterator(
            new ArrayList<>(Arrays.asList(InfrastructureMapping.builder().id("1").accountIdentifier(accountId1).build(),
                                InfrastructureMapping.builder().id("2").accountIdentifier(accountId2).build(),
                                InfrastructureMapping.builder().id("3").accountIdentifier(accountId3).build(),
                                InfrastructureMapping.builder().id("4").accountIdentifier(deletedAccount1).build(),
                                InfrastructureMapping.builder().id("5").accountIdentifier(deletedAccount2).build()))
                .iterator()));

    cleanupInfrastructureMappingNg.migrate();

    verify(repository, times(2)).deleteById(anyString());
    verify(repository, times(1)).deleteById("4");
    verify(repository, times(1)).deleteById("5");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testNoEntryEligibleForDeletion() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(accountIds);
    when(mongoTemplate.stream(
             new Query(new Criteria()).limit(NO_LIMIT).cursorBatchSize(10000), InfrastructureMapping.class))
        .thenReturn(createCloseableIterator(
            new ArrayList<>(Arrays.asList(InfrastructureMapping.builder().id("1").accountIdentifier(accountId1).build(),
                                InfrastructureMapping.builder().id("2").accountIdentifier(accountId2).build(),
                                InfrastructureMapping.builder().id("3").accountIdentifier(accountId3).build(),
                                InfrastructureMapping.builder().id("4").accountIdentifier(accountId1).build(),
                                InfrastructureMapping.builder().id("5").accountIdentifier(accountId2).build()))
                .iterator()));

    cleanupInfrastructureMappingNg.migrate();

    verifyNoInteractions(repository);
  }

  private <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}