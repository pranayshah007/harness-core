/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.DAYS_SINCE_LAST_DEPLOYED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.REPLICAS;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesDataPointParserFactoryTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private KubernetesDataPointParserFactory factory;
  @Mock private KubernetesReplicasParser kubernetesReplicasParser;
  @Mock private KubernetesLastDeployedParser kubernetesLastDeployedParser;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetParser() {
    assertEquals(kubernetesReplicasParser, factory.getParser(REPLICAS));
    assertEquals(kubernetesLastDeployedParser, factory.getParser(DAYS_SINCE_LAST_DEPLOYED));
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetParserUnsupported() {
    factory.getParser("unsupported");
  }
}
