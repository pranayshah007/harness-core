/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboards;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.dashboard.PMSLandingDashboardServiceImpl;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PMSLandingDashboardServiceImplTest {
  @Mock private DSLContext dsl;
  @InjectMocks PMSLandingDashboardServiceImpl pmsLandingDashboardService;

  private String ACC_ID = "acc_id";
  private String ORG_ID = "org_id";
  private String PROJ_ID = "pro_id";
  private Long START_TIME = 3 * DAY_IN_MS;
  private Long END_TIME = 5 * DAY_IN_MS;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetPipelinesCountForEmptyOrgProjectIdentifiers() {
    PipelinesCount pipelinesCount = pmsLandingDashboardService.getPipelinesCount("", null, 0L, 0L);
    Assertions.assertThat(pipelinesCount.getTotalCount()).isEqualTo(0L);
    Assertions.assertThat(pipelinesCount.getNewCount()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetExecutionsCountForEmptyOrgProjectIdentifiers() {
    ExecutionsCount executionsCount = pmsLandingDashboardService.getExecutionsCount("", null, 0L, 0L);
    Assertions.assertThat(executionsCount.getTotalCount()).isEqualTo(0L);
    Assertions.assertThat(executionsCount.getNewCount()).isEqualTo(0L);
  }
}
