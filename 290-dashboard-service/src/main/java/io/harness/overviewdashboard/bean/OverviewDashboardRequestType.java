/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.bean;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum OverviewDashboardRequestType {
  GET_PROJECT_LIST,
  GET_CD_TOP_PROJECT_LIST,
  GET_ACTIVE_DEPLOYMENTS_INFO,
  GET_MOST_ACTIVE_SERVICES,
  GET_DEPLOYMENT_STATS_SUMMARY,
  GET_SERVICES_COUNT,
  GET_ENV_COUNT,
  GET_PIPELINES_COUNT,
  GET_PROJECTS_COUNT,
  GET_USER_COUNT
}
