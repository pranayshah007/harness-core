/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ResourceRestraintPersistenceMonitorTest extends OrchestrationStepsTestBase {
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject @InjectMocks private ResourceRestraintPersistenceMonitor persistenceMonitor;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    persistenceMonitor.registerIterators(
        IteratorConfig.builder().enabled(true).targetIntervalInSeconds(60).threadPoolCount(2).build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(PersistenceIteratorFactory.PumpExecutorOptions.class),
            eq(ResourceRestraintPersistenceMonitor.class), any(MongoPersistenceIteratorBuilder.class));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandle() {
    ResourceRestraintInstance instance =
        ResourceRestraintInstance.builder().resourceRestraintId(generateUuid()).state(ACTIVE).build();
    persistenceMonitor.handle(instance);
    verify(resourceRestraintInstanceService).processRestraint(eq(instance));
  }
}
