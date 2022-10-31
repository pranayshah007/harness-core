/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scheduler;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.quartz.JobDetail;
import org.quartz.Trigger;

public class HQuartzSchedulerTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testJobCompare() {
    JobDetail nullJob = null;
    assertThat(HQuartzScheduler.compare(nullJob, nullJob)).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testTriggerCompare() {
    Trigger nullTrigger = null;
    assertThat(HQuartzScheduler.compare(nullTrigger, nullTrigger)).isTrue();
  }
}
