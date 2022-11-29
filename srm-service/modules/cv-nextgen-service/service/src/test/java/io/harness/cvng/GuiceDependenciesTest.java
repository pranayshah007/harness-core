/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.GuiceDependenciesCalculator.DependencyGraphStats;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GuiceDependenciesTest extends CvNextGenTestBase {
  private final List<TestInput> classesToTestForDependencies = Arrays.asList(TestInput.builder()
                                                                                 .classToTest(ActivityService.class)
                                                                                 .expectedCount(11882)
                                                                                 .allowedDelta(10)
                                                                                 .allowedCycles(5007)
                                                                                 .build());
  @Inject private Injector injector;
  private final GuiceDependenciesCalculator guiceDependenciesCalculator = new GuiceDependenciesCalculator();
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @Ignore("Enable once ti team figures out how to run this every time")
  public void testGuiceGraph() throws IllegalAccessException {
    // This is a experimental tests for now so just including only few classes. If this is helpful we will include all
    // the injected classes
    // TODO - change the logic to include all the new classes from Guice bindings
    // injector.getAllBindings();
    for (TestInput testInput : classesToTestForDependencies) {
      DependencyGraphStats dependencyGraphStats =
          guiceDependenciesCalculator.dependencyGraphStats(injector.getInstance(testInput.getClassToTest()));
      double percentageChangeTotalObjects =
          (Math.abs(dependencyGraphStats.getRootNode().getCount() - testInput.getExpectedCount()) * 100)
          / testInput.getExpectedCount();
      double percentageChangeCycles =
          (Math.abs(dependencyGraphStats.getCycles().size() - testInput.getAllowedCycles()) * 100)
          / testInput.getAllowedCycles();
      if (dependencyGraphStats.getRootNode().getCount() < testInput.getExpectedCount()) {
        /***** This is great news. Congrats on improving the dependency graph. Please update the expected count to lower
         * value. *****/
        assertThat(percentageChangeTotalObjects).isLessThanOrEqualTo(2);
      }
      if (dependencyGraphStats.getCycles().size() < testInput.getAllowedCycles()) {
        /***** This is great news. Congrats on reducing number of cycles. Please update the expected count to lower
         * value. *****/
        assertThat(percentageChangeTotalObjects).isLessThanOrEqualTo(2);
      }

      /*****
       * Looks like the overall size of graph has increased by more than the allowed delta.
       * Please check if the change is expected and there is no other way to improve it and keep it low.
       * *****/
      assertThat(percentageChangeTotalObjects).isLessThanOrEqualTo(testInput.getAllowedDelta());
      /**
       * Number of cycles has increased. Please check the newly added dependencies and update the expected cycles.
       */
      assertThat(percentageChangeCycles).isLessThanOrEqualTo(2);
    }
  }

  @Value
  @Builder
  public static class TestInput {
    Class<?> classToTest;
    int expectedCount;
    double allowedDelta;
    int allowedCycles;
  }
}
