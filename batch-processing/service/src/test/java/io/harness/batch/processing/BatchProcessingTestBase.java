/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import io.harness.CategoryTest;
import io.harness.rule.LifecycleRule;

import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public abstract class BatchProcessingTestBase extends CategoryTest {
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public BatchProcessingRule batchProcessingRule = new BatchProcessingRule(lifecycleRule.getClosingFactory());

  // Rules are processed first fields, then methods and we need Mockito to inject after guice hence this should
  // be a method rule.
  @Rule
  public MockitoRule mockitoRule() {
    return MockitoJUnit.rule();
  }
}
