/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.search.entities.application.ApplicationSearchEntity;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j

public class ElasticsearchBulkMigrationHelperTest extends WingsBaseTest {
  @Mock private ElasticsearchIndexManager elasticsearchIndexManager;
  @Mock private ElasticsearchClient elasticsearchClient;
  @Inject @InjectMocks private ApplicationSearchEntity aSearchEntity;
  @Inject @InjectMocks private ElasticsearchBulkMigrationHelper elasticsearchBulkMigrationHelper;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testSearchEntityBulkMigration() throws IOException {
    Account account = new Account();
    String accountId = persistence.save(account);
    account.setUuid(accountId);

    Application application = new Application();
    application.setName("first application");
    application.setDescription("Application to test bulk sync");
    application.setAccountId(accountId);
    String applicationUuid = persistence.save(application);
    application.setUuid(applicationUuid);

    Set<SearchEntity<?>> searchEntities = new HashSet<>();
    searchEntities.add(aSearchEntity);

    String newIndexName = "newIndexName";
    String oldIndexName = "oldIndexName";
    String oldVersion = "0.1";
    String newVersion = "0.2";

    ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob =
        ElasticsearchBulkMigrationJob.builder()
            .entityClass(aSearchEntity.getClass().getCanonicalName())
            .newIndexName(newIndexName)
            .oldIndexName(oldIndexName)
            .fromVersion(oldVersion)
            .toVersion(newVersion)
            .build();

    persistence.save(elasticsearchBulkMigrationJob);

    IndexResponse indexResponse = mock(IndexResponse.class);
    when(indexResponse.status()).thenReturn(RestStatus.OK);
    when(elasticsearchClient.index(any())).thenReturn(indexResponse);
    when(elasticsearchIndexManager.createIndex(eq(newIndexName), (String) notNull())).thenReturn(true);
    when(elasticsearchIndexManager.getAliasName(aSearchEntity.getType())).thenReturn(aSearchEntity.getType());
    when(elasticsearchIndexManager.attachIndexToAlias(aSearchEntity.getType(), newIndexName)).thenReturn(true);
    when(elasticsearchIndexManager.removeIndexFromAlias(oldIndexName)).thenReturn(true);

    boolean isMigrated = elasticsearchBulkMigrationHelper.doBulkSync(searchEntities);
    assertThat(isMigrated).isEqualTo(true);

    verify(elasticsearchClient, times(1)).index(any());
    verify(elasticsearchIndexManager, times(1)).createIndex(eq(newIndexName), any());
    verify(elasticsearchIndexManager, times(1)).getAliasName(aSearchEntity.getType());
    verify(elasticsearchIndexManager, times(1)).attachIndexToAlias(aSearchEntity.getType(), newIndexName);
    verify(elasticsearchIndexManager, times(1)).removeIndexFromAlias(oldIndexName);
  }
}
