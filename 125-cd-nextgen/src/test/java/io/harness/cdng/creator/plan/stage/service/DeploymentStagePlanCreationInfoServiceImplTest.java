/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.service;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.repositories.executions.DeploymentStagePlanCreationInfoRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentStagePlanCreationInfoServiceImplTest extends CategoryTest {
  @Mock private DeploymentStagePlanCreationInfoRepository deploymentStagePlanCreationInfoRepository;
  @InjectMocks @Inject private DeploymentStagePlanCreationInfoServiceImpl deploymentStagePlanCreationInfoService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSave() {
    DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfo = DeploymentStagePlanCreationInfo.builder().build();
    deploymentStagePlanCreationInfoService.save(deploymentStagePlanCreationInfo);
    verify(deploymentStagePlanCreationInfoRepository, times(1)).save(deploymentStagePlanCreationInfo);
  }
}
