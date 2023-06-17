/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ResourceConstraintInstance;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.utils.ResourceConstraintTestConstants;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ResourceConstraintBackupHandlerTest extends WingsBaseTest {
  @Mock private ResourceConstraintService mockResourceConstraintService;
  @Mock private PersistenceIteratorFactory mockPersistenceIteratorFactory;
  @InjectMocks @Inject private ResourceConstraintBackupHandler resourceConstraintBackupHandler;

  private ResourceConstraintInstance resourceConstraintInstance;

  @Before
  public void setUp() throws Exception {
    resourceConstraintInstance = ResourceConstraintInstance.builder()
                                     .resourceConstraintId(ResourceConstraintTestConstants.RESOURCE_CONSTRAINT_ID)
                                     .build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    resourceConstraintBackupHandler.createAndStartIterator(PumpExecutorOptions.builder()
                                                               .name("ResourceConstraint-Backup")
                                                               .poolSize(10)
                                                               .interval(Duration.ofSeconds(60))
                                                               .build(),
        Duration.ofSeconds(30));
    verify(mockPersistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(PumpExecutorOptions.class),
            eq(ResourceConstraintBackupHandler.class), any(MongoPersistenceIteratorBuilder.class));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testHandleBlockedInstance() {
    resourceConstraintInstance.setState(State.BLOCKED.name());
    resourceConstraintBackupHandler.handle(resourceConstraintInstance);
    verify(mockResourceConstraintService, times(1))
        .updateBlockedConstraints(Sets.newHashSet(resourceConstraintInstance.getResourceConstraintId()));
    verify(mockResourceConstraintService, never()).updateActiveConstraintForInstance(any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testHandleActiveInstance() {
    resourceConstraintInstance.setState(State.ACTIVE.name());
    when(mockResourceConstraintService.updateActiveConstraintForInstance(resourceConstraintInstance)).thenReturn(true);
    resourceConstraintBackupHandler.handle(resourceConstraintInstance);
    verify(mockResourceConstraintService, times(1))
        .updateBlockedConstraints(Sets.newHashSet(resourceConstraintInstance.getResourceConstraintId()));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCatchExceptionInUpdateResourceConstraint() {
    resourceConstraintInstance.setState(State.ACTIVE.name());
    doThrow(new InvalidRequestException("error"))
        .when(mockResourceConstraintService)
        .updateActiveConstraintForInstance(resourceConstraintInstance);
    resourceConstraintBackupHandler.handle(resourceConstraintInstance);
    verify(mockResourceConstraintService, never()).updateBlockedConstraints(any());

    doThrow(new RuntimeException())
        .when(mockResourceConstraintService)
        .updateActiveConstraintForInstance(resourceConstraintInstance);
    resourceConstraintBackupHandler.handle(resourceConstraintInstance);
    verify(mockResourceConstraintService, never()).updateBlockedConstraints(any());
  }
}
