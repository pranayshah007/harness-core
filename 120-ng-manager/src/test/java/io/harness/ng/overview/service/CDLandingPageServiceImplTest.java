/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.RAFAEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.TimeScaleDAL;
import io.harness.dashboards.LandingPageDeploymentCount;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class CDLandingPageServiceImplTest extends CategoryTest {
  @Mock TimeScaleDAL timeScaleDAL;
  @InjectMocks @Spy private CDLandingPageServiceImpl cdLandingPageService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testGetDeploymentCount() {
    LandingPageDeploymentCount landingPageDeploymentCount = cdLandingPageService.getDeploymentCount();
    assertThat(landingPageDeploymentCount.getValue()).isEqualTo(0);

    doReturn(5).when(timeScaleDAL).getDeploymentCount();

    landingPageDeploymentCount = cdLandingPageService.getDeploymentCount();
    assertThat(landingPageDeploymentCount.getValue()).isEqualTo(5);
  }
}