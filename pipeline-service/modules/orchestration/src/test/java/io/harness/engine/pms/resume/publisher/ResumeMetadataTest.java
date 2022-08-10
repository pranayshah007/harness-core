/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume.publisher;

import static io.harness.rule.OwnerRule.SAHIL;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(NodeExecution.class)
public class ResumeMetadataTest extends CategoryTest {
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testIfStepParametersSentAreResolvedOne() {
    NodeExecution nodeExecution = PowerMockito.mock(NodeExecution.class);
    ResumeMetadata.fromNodeExecution(nodeExecution);
    Mockito.verify(nodeExecution).getUuid();
    Mockito.verify(nodeExecution).getMode();
    Mockito.verify(nodeExecution).getAmbiance();
    Mockito.verify(nodeExecution).getResolvedStepParametersBytes();
    Mockito.verify(nodeExecution).obtainLatestExecutableResponse();
    Mockito.verify(nodeExecution).getModule();
    Mockito.verifyNoMoreInteractions(nodeExecution);
  }
}
