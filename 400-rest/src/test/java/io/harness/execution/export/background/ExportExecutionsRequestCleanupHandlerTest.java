/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.RequestTestUtils;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExportExecutionsRequestCleanupHandlerTest extends WingsBaseTest {
  @Mock private PersistenceIteratorFactory mockPersistenceIteratorFactory;
  @Mock private ExportExecutionsService exportExecutionsService;
  @Inject @InjectMocks private ExportExecutionsRequestCleanupHandler exportExecutionsRequestCleanupHandler;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    exportExecutionsRequestCleanupHandler.createAndStartIterator(PumpExecutorOptions.builder()
                                                                     .name("ExportExecutionsRequestCleanupHandler")
                                                                     .interval(Duration.ofHours(1))
                                                                     .build(),
        Duration.ofMinutes(45));
    verify(mockPersistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(PumpExecutorOptions.class),
            eq(ExportExecutionsRequestCleanupHandler.class), any(MongoPersistenceIteratorBuilder.class));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testHandle() {
    exportExecutionsRequestCleanupHandler.handle(null);
    verify(exportExecutionsService, never()).expireRequest(any());

    exportExecutionsRequestCleanupHandler.handle(
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY));
    verify(exportExecutionsService, times(1)).expireRequest(any());

    doThrow(new RuntimeException("")).when(exportExecutionsService).expireRequest(any());
    exportExecutionsRequestCleanupHandler.handle(
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY));
    verify(exportExecutionsService, times(2)).expireRequest(any());
  }
}
