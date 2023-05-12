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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeSubscriber;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.search.entities.application.ApplicationSearchEntity;

import com.google.inject.Inject;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ElasticsearchBulkSyncTaskTest extends WingsBaseTest {
  @Mock private ElasticsearchIndexManager elasticsearchIndexManager;
  @Mock private ElasticsearchSyncHelper elasticsearchSyncHelper;
  @Mock private ElasticsearchBulkMigrationHelper elasticsearchBulkMigrationHelper;
  @Inject @InjectMocks private ApplicationSearchEntity aSearchEntity;
  @Inject @InjectMocks private ElasticsearchBulkSyncTask elasticsearchBulkSyncTask;
  @Inject private HPersistence persistence;

  private final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testBulkSyncProcess() {
    String newIndexName = "newIndexName";
    String oldIndexName = "oldIndexName";
    String oldVersion = "0.05";
    String newVersion = "0.1";
    ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob =
        ElasticsearchBulkMigrationJob.builder()
            .entityClass(aSearchEntity.getClass().getCanonicalName())
            .fromVersion(oldVersion)
            .toVersion(newVersion)
            .oldIndexName(oldIndexName)
            .newIndexName(newIndexName)
            .build();
    persistence.save(elasticsearchBulkMigrationJob);

    SearchEntityIndexState searchEntityIndexState =
        new SearchEntityIndexState(aSearchEntity.getClass().getCanonicalName(), "0.05", oldIndexName, false);
    persistence.save(searchEntityIndexState);

    Future f = threadPoolExecutor.submit(() -> {
      try {
        Thread.sleep(Long.MAX_VALUE);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    String token = "token";
    String uuid = "uuid";
    String token1 = "token1";
    String uuid1 = "uuid1";
    ChangeEvent changeEvent = new ChangeEvent<>(token, ChangeType.DELETE, Application.class, uuid, null, null);
    ChangeEvent changeEvent1 = new ChangeEvent<>(token1, ChangeType.INSERT, Application.class, uuid1, null, null);

    when(elasticsearchIndexManager.deleteIndex(newIndexName)).thenReturn(true);

    doAnswer(invocationOnMock -> {
      ChangeSubscriber<?> changeSubscriber = invocationOnMock.getArgument(0, ChangeSubscriber.class);
      changeSubscriber.onChange(changeEvent);
      changeSubscriber.onChange(changeEvent1);
      return f;
    })
        .when(elasticsearchSyncHelper)
        .startChangeListeners(any());

    when(elasticsearchBulkMigrationHelper.doBulkSync(any())).thenReturn(true);

    doAnswer(invocationOnMock -> {
      f.cancel(true);
      return null;
    })
        .when(elasticsearchSyncHelper)
        .stopChangeListeners();

    ElasticsearchBulkSyncTaskResult elasticsearchBulkSyncTaskResult = elasticsearchBulkSyncTask.run();

    assertThat(elasticsearchBulkSyncTaskResult.isSuccessful()).isEqualTo(true);
    Queue<ChangeEvent<?>> receivedChangeEvents = elasticsearchBulkSyncTaskResult.getChangeEventsDuringBulkSync();
    assertThat(receivedChangeEvents).isNotNull();
    assertThat(receivedChangeEvents.size()).isEqualTo(1);
    ChangeEvent<?> receivedChangeEvent = receivedChangeEvents.poll();
    assertThat(receivedChangeEvent).isNotNull();
    assertThat(receivedChangeEvent.getToken()).isEqualTo(token);
    assertThat(receivedChangeEvent.getUuid()).isEqualTo(uuid);

    verify(elasticsearchIndexManager, times(1)).deleteIndex(newIndexName);
    verify(elasticsearchSyncHelper, times(1)).startChangeListeners(any());
    verify(elasticsearchSyncHelper, times(1)).stopChangeListeners();

    ArgumentCaptor<Set<SearchEntity<?>>> captor = ArgumentCaptor.forClass((Class) Set.class);
    verify(elasticsearchBulkMigrationHelper, times(1)).doBulkSync(captor.capture());
    Set<SearchEntity<?>> capturedSearchEntities = captor.getValue();
    assertThat(capturedSearchEntities.size()).isEqualTo(6);

    ElasticsearchBulkMigrationJob shouldBeDeletedJob =
        persistence.get(ElasticsearchBulkMigrationJob.class, ApplicationSearchEntity.class.getCanonicalName());
    assertThat(shouldBeDeletedJob).isNull();

    SearchEntityIndexState createdSearchEntityIndexState =
        persistence.get(SearchEntityIndexState.class, aSearchEntity.getClass().getCanonicalName());
    assertThat(createdSearchEntityIndexState).isNotNull();
    assertThat(createdSearchEntityIndexState.getSyncVersion()).isEqualTo(ApplicationSearchEntity.VERSION);
    assertThat(createdSearchEntityIndexState.isRecreateIndex()).isEqualTo(false);
    assertThat(createdSearchEntityIndexState.shouldBulkSync()).isEqualTo(false);
  }
}
