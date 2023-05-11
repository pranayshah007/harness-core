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
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class ElasticsearchRealtimeSyncTaskTest extends WingsBaseTest {
  private final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
  @Mock private ElasticsearchSyncHelper elasticsearchSyncHelper;
  @Mock private ChangeEventProcessor changeEventProcessor;
  @Inject @InjectMocks private ElasticsearchRealtimeSyncTask elasticsearchRealtimeSyncTask;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testRealtimeSyncProcess() throws InterruptedException, ExecutionException, TimeoutException {
    String token = "token";
    String uuid = "uuid";
    String token1 = "token1";
    String uuid1 = "uuid1";
    ChangeEvent changeEvent = new ChangeEvent<>(token, ChangeType.DELETE, Application.class, uuid, null, null);
    ChangeEvent changeEvent1 = new ChangeEvent<>(token1, ChangeType.INSERT, Application.class, uuid1, null, null);
    CountDownLatch latch = new CountDownLatch(1);

    Queue<ChangeEvent<?>> changeEvents = new LinkedList<>();
    changeEvents.add(changeEvent1);

    doAnswer(invocationOnMock -> {
      ChangeSubscriber<?> changeSubscriber = invocationOnMock.getArgument(0, ChangeSubscriber.class);
      changeSubscriber.onChange(changeEvent);
      latch.countDown();
      return null;
    })
        .when(elasticsearchSyncHelper)
        .startChangeListeners(any());

    when(elasticsearchSyncHelper.checkIfAnyChangeListenerIsAlive()).thenReturn(true);

    Future realTimeSyncFuture = threadPoolExecutor.submit(() -> {
      try {
        elasticsearchRealtimeSyncTask.run(changeEvents);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    assertThat(realTimeSyncFuture.isDone()).isFalse();

    latch.await(10000, TimeUnit.SECONDS);
    verify(elasticsearchSyncHelper, times(1)).startChangeListeners(any());
    verify(changeEventProcessor, times(2)).processChangeEvent(any());

    elasticsearchRealtimeSyncTask.stop();
    realTimeSyncFuture.get(10000, TimeUnit.SECONDS);
    verify(elasticsearchSyncHelper, times(2)).stopChangeListeners();
  }
}
