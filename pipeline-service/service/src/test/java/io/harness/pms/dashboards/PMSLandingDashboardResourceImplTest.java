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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboard.PMSLandingDashboardResourceImpl;
import io.harness.pms.dashboard.PMSLandingDashboardService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PMSLandingDashboardResourceImplTest {
  @Mock PMSLandingDashboardService pmsLandingDashboardService;
  @InjectMocks PMSLandingDashboardResourceImpl pmsLandingDashboardResource;

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
  public void testGetPipelinesCount() {
    List<OrgProjectIdentifier> orgProjectIdentifierList =
        Arrays.asList(OrgProjectIdentifier.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).build());
    LandingDashboardRequestPMS landingDashboardRequestPMS =
        LandingDashboardRequestPMS.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();

    doReturn(PipelinesCount.builder().totalCount(5L).newCount(1L).build())
        .when(pmsLandingDashboardService)
        .getPipelinesCount(ACC_ID, orgProjectIdentifierList, START_TIME, END_TIME);

    ResponseDTO<PipelinesCount> pipelinesCountResponseDTO =
        pmsLandingDashboardResource.getPipelinesCount(ACC_ID, START_TIME, END_TIME, landingDashboardRequestPMS);
    assertThat(pipelinesCountResponseDTO.getData().getTotalCount()).isEqualTo(5L);
    assertThat(pipelinesCountResponseDTO.getData().getNewCount()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetExecutionsCount() {
    List<OrgProjectIdentifier> orgProjectIdentifierList =
        Arrays.asList(OrgProjectIdentifier.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).build());
    LandingDashboardRequestPMS landingDashboardRequestPMS =
        LandingDashboardRequestPMS.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();

    doReturn(ExecutionsCount.builder().totalCount(5L).newCount(1L).build())
        .when(pmsLandingDashboardService)
        .getExecutionsCount(ACC_ID, orgProjectIdentifierList, START_TIME, END_TIME);

    ResponseDTO<ExecutionsCount> executionsCountResponseDTO =
        pmsLandingDashboardResource.getExecutionsCount(ACC_ID, START_TIME, END_TIME, landingDashboardRequestPMS);
    assertThat(executionsCountResponseDTO.getData().getTotalCount()).isEqualTo(5L);
    assertThat(executionsCountResponseDTO.getData().getNewCount()).isEqualTo(1L);
  }
}
