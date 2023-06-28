/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewChartType;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewTimeRange;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CEViewServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private CEViewServiceImpl ceViewService;
  @Mock private CEViewDao ceViewDao;
  @Mock private CEViewFolderDao ceViewFolderDao;
  @Mock private CEReportScheduleDao ceReportScheduleDao;
  @Mock private FeatureFlagService featureFlagService;

  private static final String ACCOUNT_ID = "account_id";
  private static final String VIEW_NAME = "view_name";
  private static final String UUID = "uuid";
  private static final String UUID_1 = "uuid1";
  private static final String FOLDER_NAME = "folder_name";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testSave() {
    doReturn(true).when(ceViewDao).save(any());
    doReturn(ceViewFolder()).when(ceViewFolderDao).getDefaultFolder(any());
    doReturn(ceViewFolder()).when(ceViewFolderDao).getSampleFolder(any());
    CEView ceView = ceViewService.save(ceView(), false);
    assertThat(ceView.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceView.getName()).isEqualTo(VIEW_NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGet() {
    final CEView ceView = ceView();
    doReturn(ceView).when(ceViewDao).get(any());
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
            .viewState(ViewState.COMPLETED)
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
            .viewState(ViewState.COMPLETED)
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
        .name(VIEW_NAME)
        .accountId(ACCOUNT_ID)
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
}
