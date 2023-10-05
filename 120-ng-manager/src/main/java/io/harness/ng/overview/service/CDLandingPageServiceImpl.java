/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.TimeScaleDAL;
import io.harness.dashboards.LandingPageDeploymentCount;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class CDLandingPageServiceImpl implements CDLandingPageService {
  @Inject private TimeScaleDAL timeScaleDAL;

  public LandingPageDeploymentCount getDeploymentCount() {
    return LandingPageDeploymentCount.builder().value(timeScaleDAL.getDeploymentCount()).build();
  }
}
