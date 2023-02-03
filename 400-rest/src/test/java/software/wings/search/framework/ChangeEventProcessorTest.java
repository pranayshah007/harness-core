/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;

public class ChangeEventProcessorTest extends WingsBaseTest {
  @Spy private Set<SearchEntity<?>> searchEntities = new HashSet<>();
  @Inject @InjectMocks private ChangeEventProcessor changeEventProcessor;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testProcessChange() {
    SearchEntity searchEntity = mock(SearchEntity.class);
    ChangeHandler changeHandler = mock(ChangeHandler.class);
    Class<? extends PersistentEntity> sourceClass = PersistentEntity.class;
    String token = "__TOKEN__";
    String uuid = "__UUID__";
    ChangeEvent changeEvent = new ChangeEvent<>(token, ChangeType.DELETE, sourceClass, uuid, null, null);
    List<Class<? extends PersistentEntity>> subscriptionEntities = new ArrayList<>();
    subscriptionEntities.add(sourceClass);
    searchEntities.add(searchEntity);

    changeEventProcessor.startProcessingChangeEvents(new HashSet<>());
    when(searchEntity.getSubscriptionEntities()).thenReturn(subscriptionEntities);
    when(searchEntity.getChangeHandler()).thenReturn(changeHandler);
    when(changeHandler.handleChange(changeEvent)).thenReturn(true).thenThrow(new RuntimeException("Dummy error"));

    boolean result = changeEventProcessor.processChangeEvent(changeEvent);
    assertThat(result).isTrue();

    result = changeEventProcessor.processChangeEvent(changeEvent);
    assertThat(result).isTrue();

    await().atMost(2, TimeUnit.MINUTES).until(() -> !changeEventProcessor.isAlive());

    boolean checkIfAlive = changeEventProcessor.isAlive();
    assertThat(checkIfAlive).isFalse();

    verify(searchEntity, times(2)).getChangeHandler();
    verify(changeHandler, times(2)).handleChange(changeEvent);

    changeEventProcessor.shutdown();
  }
}
