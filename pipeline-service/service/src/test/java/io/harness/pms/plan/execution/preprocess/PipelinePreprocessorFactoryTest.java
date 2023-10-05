/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.preprocess;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelinePreprocessorFactoryTest extends CategoryTest {
  PipelinePreprocessorFactory factory = new PipelinePreprocessorFactory();
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInstance() {
    assertThat(factory.getProcessorInstance(HarnessYamlVersion.V1)).isInstanceOf(PipelineV1Preprocessor.class);
    assertThat(factory.getProcessorInstance(HarnessYamlVersion.V0)).isNull();
  }
}
