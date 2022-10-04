/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.rule.OwnerRule.GEORGE;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BatchProcessingMorphiaClassesTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testBatchProcessingClassesModule() {
    new BatchProcessingMorphiaRegistrar().testClassesModule();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testEventSearchAndList() {
    // MorphiaModule.getInstance().testAutomaticSearch();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testBatchProcessingImplementationClassesModule() {
    new BatchProcessingMorphiaRegistrar().testImplementationClassesModule();
  }
}
