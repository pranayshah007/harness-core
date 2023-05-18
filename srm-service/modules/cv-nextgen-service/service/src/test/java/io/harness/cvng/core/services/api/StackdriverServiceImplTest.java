/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDetail;
import io.harness.cvng.core.services.impl.StackdriverServiceImpl;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StackdriverServiceImplTest extends CategoryTest {
  @Mock private OnboardingService onboardingService;
  @InjectMocks private StackdriverService stackdriverService = new StackdriverServiceImpl();
  private String accountId;
  private String connectorIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  @Captor private ArgumentCaptor<OnboardingRequestDTO> requestCaptor;

  @Before
  public void setup() {
    accountId = generateUuid();
    connectorIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetDashboardList() {
    when(onboardingService.getOnboardingResponse(eq(accountId), any()))
        .thenReturn(OnboardingResponseDTO.builder().result(getDashboardList(8)).build());
    PageResponse<StackdriverDashboardDTO> dashboardList = stackdriverService.listDashboards(
        accountId, connectorIdentifier, orgIdentifier, projectIdentifier, 10, 0, null, generateUuid());

    verify(onboardingService).getOnboardingResponse(eq(accountId), requestCaptor.capture());

    OnboardingRequestDTO onboardingRequestDTO = requestCaptor.getValue();
    assertThat(onboardingRequestDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(onboardingRequestDTO.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(onboardingRequestDTO.getAccountId()).isEqualTo(accountId);
    assertThat(onboardingRequestDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(onboardingRequestDTO.getDataCollectionRequest()).isNotNull();

    DataCollectionRequest request = onboardingRequestDTO.getDataCollectionRequest();
    assertThat(request.getType().name()).isEqualTo(DataCollectionRequestType.STACKDRIVER_DASHBOARD_LIST.name());
    assertThat(dashboardList).isNotNull();
    assertThat(dashboardList.getContent().size()).isEqualTo(8);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetDashboardDetail() throws Exception {
    String textLoad = Resources.toString(
        StackdriverServiceImplTest.class.getResource("/stackdriver/dashboard-detail-response.json"), Charsets.UTF_8);

    when(onboardingService.getOnboardingResponse(eq(accountId), any()))
        .thenReturn(OnboardingResponseDTO.builder().result(JsonUtils.asObject(textLoad, Object.class)).build());
    List<StackdriverDashboardDetail> dashboardDetailList =
        stackdriverService.getDashboardDetails(accountId, connectorIdentifier, orgIdentifier, projectIdentifier,
            "projects/674494598921/dashboards/dfd3572d-2aef-46d9-b4a2-f1d546f46110", generateUuid());

    verify(onboardingService).getOnboardingResponse(eq(accountId), requestCaptor.capture());

    OnboardingRequestDTO onboardingRequestDTO = requestCaptor.getValue();
    assertThat(onboardingRequestDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(onboardingRequestDTO.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(onboardingRequestDTO.getAccountId()).isEqualTo(accountId);
    assertThat(onboardingRequestDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(onboardingRequestDTO.getDataCollectionRequest()).isNotNull();

    DataCollectionRequest request = onboardingRequestDTO.getDataCollectionRequest();
    assertThat(request.getType().name()).isEqualTo(DataCollectionRequestType.STACKDRIVER_DASHBOARD_GET.name());
    assertThat(dashboardDetailList).isNotNull();
    dashboardDetailList.forEach(dashboardDetail -> {
      assertThat(dashboardDetail.getWidgetName()).isNotEmpty();
      assertThat(dashboardDetail.getDataSetList()).isNotEmpty();
    });
  }

  private List<StackdriverDashboardDTO> getDashboardList(int count) {
    List<StackdriverDashboardDTO> dashboardDTOList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      dashboardDTOList.add(
          StackdriverDashboardDTO.builder().name("dashboard-" + i).path("dashboardPath - " + i).build());
    }
    return dashboardDTOList;
  }
}
