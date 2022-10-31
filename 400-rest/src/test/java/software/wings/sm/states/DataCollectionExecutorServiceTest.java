/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.rule.OwnerRule.SRIRAM;

import static junit.framework.TestCase.fail;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.DataCollectionExecutorService;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DataCollectionExecutorServiceTest extends APMStateVerificationTestBase {
  @Inject private DataCollectionExecutorService executorService;

  @Test(expected = WingsException.class)
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void executeParallelWithException() throws IOException {
    List<Callable<Boolean>> callables = new ArrayList<>();
    callables.add(() -> true);
    callables.add(() -> { throw new RuntimeException("fail on purpose"); });
    executorService.executeParrallel(callables);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void executeParallel() {
    List<Callable<Boolean>> callables = new ArrayList<>();
    callables.add(() -> true);
    callables.add(() -> { throw new RuntimeException("fail on purpose"); });
    try {
      executorService.executeParrallel(callables);
      fail();
    } catch (WingsException ex) {
      // do nothing
    }
    callables.remove(1);
    executorService.executeParrallel(callables);
  }
}
