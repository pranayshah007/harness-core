/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.ccm.views.entities.ViewState.COMPLETED;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.dto.ViewTimeRangeDto;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewChartType;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewTimeRange;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.ViewFilterBuilderHelper;
import io.harness.ccm.views.helper.ViewTimeRangeHelper;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class CEViewServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private CEViewServiceImpl ceViewService;
  @Mock private CEViewDao ceViewDao;
  @Mock private CEViewFolderDao ceViewFolderDao;
  @Mock private CEReportScheduleDao ceReportScheduleDao;
  @Mock private ViewsBillingService viewsBillingService;
  @Mock private ViewsQueryHelper viewsQueryHelper;
  @Mock private ViewFilterBuilderHelper viewFilterBuilderHelper;
  @Mock private ViewTimeRangeHelper viewTimeRangeHelper;
  @Mock private FeatureFlagService featureFlagService;

  private static final String ACCOUNT_ID = "account_id";
  private static final String VIEW_NAME = "view_name";
  private static final String UUID = "uuid";
  private static final String UUID_1 = "uuid1";
  private static final String FOLDER_NAME = "folder_name";
  private static final String FOLDER_ID = "folder_id";
  private static final String BUSINESS_MAPPING_UUID = "business_mapping_uuid";
  private static final String NEW_BUSINESS_MAPPING_NAME = "business_mapping_name";

  private static final Double TOTAL_COST = 1000.0;
  private static final Double IDLE_COST = 200.0;
  private static final Double UNALLOCATED_COST = 400.0;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(ceViewDao.get(UUID)).thenReturn(ceView());
    when(ceViewDao.save(any())).thenReturn(true);
    when(ceViewDao.get(any())).thenReturn(ceView());
    when(ceViewDao.getPerspectivesByIds(ACCOUNT_ID, Collections.singletonList(UUID)))
        .thenReturn(Collections.singletonList(ceView()));
    when(ceViewDao.updateTotalCost(UUID, ACCOUNT_ID, TOTAL_COST)).thenReturn(ceViewWithUpdatedTotalCost());
    when(ceViewDao.list(ACCOUNT_ID)).thenReturn(Collections.singletonList(ceView()));
    when(ceViewDao.findByAccountIdAndState(ACCOUNT_ID, COMPLETED)).thenReturn(Collections.singletonList(ceView()));
    when(ceViewDao.findByAccountIdAndBusinessMapping(ACCOUNT_ID, BUSINESS_MAPPING_UUID))
        .thenReturn(Collections.singletonList(ceView()));

    when(ceViewFolderDao.getDefaultFolder(any())).thenReturn(ceViewFolder());
    when(ceViewFolderDao.getSampleFolder(any())).thenReturn(ceViewFolder());

    when(viewTimeRangeHelper.getStartEndTime(any())).thenReturn(ViewTimeRangeDto.builder().startTime(0L).build());

    when(viewsBillingService.getCostData(any(), any(), any())).thenReturn(getMockViewCostData());
    when(viewsBillingService.getTrendStatsDataNg(any(), any(), any(), any())).thenReturn(getMockViewTrendData());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testSave() {
    CEView ceView = ceViewService.save(ceView(), false);
    assertThat(ceView.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceView.getName()).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClone() {
    CEView ceView = ceViewService.save(ceView(), true);
    assertThat(ceView.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceView.getName()).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetActualCostForPerspective() {
    double actualCost = ceViewService.getActualCostForPerspectiveBudget(ACCOUNT_ID, UUID);
    assertThat(actualCost).isEqualTo(TOTAL_COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGet() {
    CEView ceViewReturned = ceViewService.get(UUID);
    assertThat(ceViewReturned.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewReturned.getName()).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testUpdate() {
    final CEView ceView = ceView();
    doReturn(ceView).when(ceViewDao).update(any());
    CEView ceViewUpdated = ceViewService.update(ceView());
    assertThat(ceViewUpdated.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewUpdated.getName()).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetPerspectiveFolderIds() {
    Set<String> folderIds = ceViewService.getPerspectiveFolderIds(ACCOUNT_ID, Collections.singletonList(UUID));
    assertThat(folderIds).isNotNull();
    assertThat(folderIds.size()).isEqualTo(1);
    assertThat(folderIds.contains(FOLDER_ID)).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetPerspectiveIdAndFolderId() {
    HashMap<String, String> perspectiveIdToFolderIdMapping =
        ceViewService.getPerspectiveIdAndFolderId(ACCOUNT_ID, Collections.singletonList(UUID));
    assertThat(perspectiveIdToFolderIdMapping).isNotNull();
    assertThat(perspectiveIdToFolderIdMapping.get(UUID)).isEqualTo(FOLDER_ID);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdateBusinessMappingName() {
    ceViewService.updateBusinessMappingName(ACCOUNT_ID, BUSINESS_MAPPING_UUID, NEW_BUSINESS_MAPPING_NAME);
    verify(ceViewDao).updateBusinessMappingName(ACCOUNT_ID, BUSINESS_MAPPING_UUID, NEW_BUSINESS_MAPPING_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdateTotalCost() {
    CEView view = ceViewService.updateTotalCost(ceView());
    assertThat(view.getUuid()).isEqualTo(UUID);
    assertThat(view.getTotalCost()).isEqualTo(TOTAL_COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true).when(ceViewDao).delete(any(), any());
    final boolean isDeleted = ceViewService.delete(UUID, ACCOUNT_ID);
    assertThat(isDeleted).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhileSavingCustomField() {
    doReturn(ceView()).when(ceViewDao).findByName(ACCOUNT_ID, VIEW_NAME);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> ceViewService.save(ceView(), false));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldThrowExceptionViewsExceedLimit() {
    doReturn(new ArrayList<CEView>(Collections.nCopies(10000, null))).when(ceViewDao).findByAccountId(ACCOUNT_ID, null);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> ceViewService.save(ceView(), false));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAllViewsTest() {
    doReturn(getAllViewsForAccount()).when(ceViewDao).findByAccountId(ACCOUNT_ID, null);
    doReturn(Collections.emptyList()).when(ceReportScheduleDao).getReportSettingByViewIds(any(), any());
    List<QLCEView> allViews = ceViewService.getAllViews(ACCOUNT_ID, false, null);
    assertThat(allViews.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAllViewsForFolderTest() {
    doReturn(getAllViewsForAccount()).when(ceViewDao).findByAccountId(ACCOUNT_ID, null);
    doReturn(Collections.emptyList()).when(ceReportScheduleDao).getReportSettingByViewIds(any(), any());
    List<QLCEView> allViews = ceViewService.getAllViews(ACCOUNT_ID, false, null);
    assertThat(allViews.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAllViewsForAccountTest() {
    List<CEView> allViews = ceViewService.getAllViews(ACCOUNT_ID);
    assertThat(allViews.size()).isEqualTo(1);
    assertThat(allViews.get(0)).isEqualTo(ceView());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAllViewsByStateTest() {
    List<CEView> allViews = ceViewService.getViewByState(ACCOUNT_ID, COMPLETED);
    assertThat(allViews.size()).isEqualTo(1);
    assertThat(allViews.get(0)).isEqualTo(ceView());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAllViewsByBusinessMappingTest() {
    List<LinkedPerspectives> perspectives =
        ceViewService.getViewsByBusinessMapping(ACCOUNT_ID, Collections.singletonList(BUSINESS_MAPPING_UUID));
    assertThat(perspectives.size()).isEqualTo(1);
    assertThat(perspectives.get(0).getPerspectiveIdAndName().get(UUID)).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void getDefaultFolderId() {
    String defaultFolderId = ceViewService.getDefaultFolderId(ACCOUNT_ID);
    CEViewFolder defaultFolder = ceViewFolder();
    assertThat(defaultFolder.getUuid()).isEqualTo(defaultFolderId);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void getSampleFolderId() {
    String sampleFolderId = ceViewService.getDefaultFolderId(ACCOUNT_ID);
    CEViewFolder sampleFolder = ceViewFolder();
    assertThat(sampleFolder.getUuid()).isEqualTo(sampleFolderId);
  }

  private List<CEView> getAllViewsForAccount() {
    List<QLCEViewField> clusterFields = ViewFieldUtils.getClusterFields();
    List<QLCEViewField> awsFields = ViewFieldUtils.getAwsFields();
    final QLCEViewField awsService = awsFields.get(0);
    final QLCEViewField awsAccount = awsFields.get(1);
    final QLCEViewField clusterName = clusterFields.get(0);
    final QLCEViewField namespace = clusterFields.get(1);

    CEView awsServiceView =
        CEView.builder()
            .uuid(UUID)
            .accountId(ACCOUNT_ID)
            .name(VIEW_NAME + "0")
            .dataSources(ImmutableList.of(ViewFieldIdentifier.AWS))
            .viewRules(
                Arrays.asList(ViewRule.builder()
                                  .viewConditions(Arrays.asList(
                                      ViewIdCondition.builder()
                                          .viewField(ViewField.builder()
                                                         .fieldName(awsService.getFieldName())
                                                         .fieldId(awsService.getFieldId())
                                                         .identifier(ViewFieldIdentifier.AWS)
                                                         .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                         .build())
                                          .viewOperator(ViewIdOperator.IN)
                                          .values(Arrays.asList("service1"))
                                          .build()))
                                  .build()))
            .viewState(COMPLETED)
            .viewType(ViewType.CUSTOMER)
            .viewTimeRange(ViewTimeRange.builder().viewTimeRangeType(ViewTimeRangeType.LAST_7).build())
            .viewVersion("v1")
            .viewVisualization(ViewVisualization.builder()
                                   .chartType(ViewChartType.STACKED_TIME_SERIES)
                                   .granularity(ViewTimeGranularity.DAY)
                                   .groupBy(ViewField.builder()
                                                .identifier(ViewFieldIdentifier.AWS)
                                                .fieldName(awsAccount.getFieldName())
                                                .fieldId(awsAccount.getFieldId())
                                                .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                .build())
                                   .build())
            .build();

    CEView clusterView =
        CEView.builder()
            .uuid(UUID_1)
            .accountId(ACCOUNT_ID)
            .name(VIEW_NAME + "1")
            .dataSources(ImmutableList.of(ViewFieldIdentifier.CLUSTER))
            .viewRules(
                Arrays.asList(ViewRule.builder()
                                  .viewConditions(Arrays.asList(
                                      ViewIdCondition.builder()
                                          .viewField(ViewField.builder()
                                                         .fieldName(clusterName.getFieldName())
                                                         .fieldId(clusterName.getFieldId())
                                                         .identifier(ViewFieldIdentifier.CLUSTER)
                                                         .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                                                         .build())
                                          .viewOperator(ViewIdOperator.NOT_IN)
                                          .values(Arrays.asList("cluster1"))
                                          .build()))
                                  .build()))
            .viewState(COMPLETED)
            .viewType(ViewType.SAMPLE)
            .viewTimeRange(ViewTimeRange.builder().viewTimeRangeType(ViewTimeRangeType.LAST_MONTH).build())
            .viewVersion("v1")
            .viewVisualization(ViewVisualization.builder()
                                   .chartType(ViewChartType.STACKED_LINE_CHART)
                                   .granularity(ViewTimeGranularity.MONTH)
                                   .groupBy(ViewField.builder()
                                                .identifier(ViewFieldIdentifier.CLUSTER)
                                                .fieldName(namespace.getFieldName())
                                                .fieldId(namespace.getFieldId())
                                                .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                                                .build())
                                   .build())
            .build();

    return Arrays.asList(awsServiceView, clusterView);
  }

  private CEView ceView() {
    List<QLCEViewField> gcpFields = ViewFieldUtils.getGcpFields();
    final QLCEViewField gcpProduct = gcpFields.get(0);
    return CEView.builder()
        .uuid(UUID)
        .name(VIEW_NAME)
        .accountId(ACCOUNT_ID)
        .folderId(FOLDER_ID)
        .viewState(COMPLETED)
        .viewRules(Arrays.asList(ViewRule.builder()
                                     .viewConditions(Arrays.asList(
                                         ViewIdCondition.builder()
                                             .viewField(ViewField.builder()
                                                            .fieldName(gcpProduct.getFieldName())
                                                            .fieldId(gcpProduct.getFieldId())
                                                            .identifier(ViewFieldIdentifier.GCP)
                                                            .identifierName(ViewFieldIdentifier.GCP.getDisplayName())
                                                            .build())
                                             .viewOperator(ViewIdOperator.IN)
                                             .values(Arrays.asList("product"))
                                             .build()))
                                     .build()))
        .build();
  }

  private CEView ceViewWithUpdatedTotalCost() {
    List<QLCEViewField> gcpFields = ViewFieldUtils.getGcpFields();
    final QLCEViewField gcpProduct = gcpFields.get(0);
    return CEView.builder()
        .uuid(UUID)
        .name(VIEW_NAME)
        .accountId(ACCOUNT_ID)
        .folderId(FOLDER_ID)
        .totalCost(TOTAL_COST)
        .viewState(COMPLETED)
        .viewRules(Arrays.asList(ViewRule.builder()
                                     .viewConditions(Arrays.asList(
                                         ViewIdCondition.builder()
                                             .viewField(ViewField.builder()
                                                            .fieldName(gcpProduct.getFieldName())
                                                            .fieldId(gcpProduct.getFieldId())
                                                            .identifier(ViewFieldIdentifier.GCP)
                                                            .identifierName(ViewFieldIdentifier.GCP.getDisplayName())
                                                            .build())
                                             .viewOperator(ViewIdOperator.IN)
                                             .values(Arrays.asList("product"))
                                             .build()))
                                     .build()))
        .build();
  }

  private CEViewFolder ceViewFolder() {
    return CEViewFolder.builder().uuid(UUID).name(FOLDER_NAME).accountId(ACCOUNT_ID).pinned(false).build();
  }

  private ViewCostData getMockViewCostData() {
    return ViewCostData.builder().cost(TOTAL_COST).idleCost(IDLE_COST).unallocatedCost(UNALLOCATED_COST).build();
  }

  private QLCEViewTrendData getMockViewTrendData() {
    return QLCEViewTrendData.builder().totalCost(QLCEViewTrendInfo.builder().value(TOTAL_COST).build()).build();
  }

  private QLCEViewFilterWrapper getMockTimeFilter() {
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build())
                        .operator(QLCEViewTimeFilterOperator.AFTER)
                        .value(0L)
                        .build())
        .build();
  }
}