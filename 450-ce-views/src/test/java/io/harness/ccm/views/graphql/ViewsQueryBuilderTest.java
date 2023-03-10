/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ViewsQueryBuilderTest extends CategoryTest {
  @Inject @InjectMocks ViewsQueryBuilder viewsQueryBuilder;

  private QLCEViewTimeFilter endTimeFilter;
  private QLCEViewTimeFilter startTimeFilter;
  private static final String awsFilter = "awsService IS NOT NULL";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    startTimeFilter = QLCEViewTimeFilter.builder()
                          .field(QLCEViewFieldInput.builder()
                                     .fieldId(ViewsMetaDataFields.START_TIME.getFieldName())
                                     .fieldName(ViewsMetaDataFields.START_TIME.getFieldName())
                                     .identifier(ViewFieldIdentifier.COMMON)
                                     .build())
                          .operator(QLCEViewTimeFilterOperator.AFTER)
                          .value(Long.valueOf(0))
                          .build();

    endTimeFilter = QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId(ViewsMetaDataFields.START_TIME.getFieldName())
                                   .fieldName(ViewsMetaDataFields.START_TIME.getFieldName())
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .build())
                        .operator(QLCEViewTimeFilterOperator.BEFORE)
                        .value(Instant.now().toEpochMilli())
                        .build();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  @Ignore("to be fixed by ccm team")
  public void testGetQueryAwsView() {
    List<QLCEViewField> awsFields = ViewFieldUtils.getAwsFields();
    final QLCEViewField awsService = awsFields.get(0);
    final QLCEViewField awsAccount = awsFields.get(1);

    List<ViewRule> viewRules = Arrays.asList(
        ViewRule.builder()
            .viewConditions(Arrays.asList(ViewIdCondition.builder()
                                              .viewField(ViewField.builder()
                                                             .fieldName(awsService.getFieldName())
                                                             .fieldId(awsService.getFieldId())
                                                             .identifier(ViewFieldIdentifier.AWS)
                                                             .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                             .build())
                                              .viewOperator(ViewIdOperator.IN)
                                              .values(Arrays.asList("service1"))
                                              .build()))
            .build());

    QLCEViewGroupBy groupBy = QLCEViewGroupBy.builder()
                                  .entityGroupBy(QLCEViewFieldInput.builder()
                                                     .fieldId(awsAccount.getFieldId())
                                                     .fieldName(awsAccount.getFieldName())
                                                     .identifier(ViewFieldIdentifier.AWS)
                                                     .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                     .build())
                                  .build();
    SelectQuery selectQuery = viewsQueryBuilder.getQuery(viewRules, Collections.emptyList(),
        Arrays.asList(startTimeFilter, endTimeFilter), Collections.singletonList(groupBy), Collections.emptyList(),
        Collections.emptyList(), "TableName", Collections.emptyList());
    assertThat(selectQuery.toString()).contains("GROUP BY awsUsageAccountId");
    assertThat(selectQuery.toString()).contains("((awsServicecode IN ('service1') )");
    assertThat(selectQuery.toString()).contains("SELECT awsUsageAccountId FROM TableName");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetQueryClusterView() {
    List<QLCEViewField> clusterFields = ViewFieldUtils.getClusterFields();
    final QLCEViewField clusterName = clusterFields.get(0);
    final QLCEViewField namespace = clusterFields.get(2);

    List<ViewRule> viewRules = Arrays.asList(
        ViewRule.builder()
            .viewConditions(
                Arrays.asList(ViewIdCondition.builder()
                                  .viewField(ViewField.builder()
                                                 .fieldName(clusterName.getFieldName())
                                                 .fieldId(clusterName.getFieldId())
                                                 .identifier(ViewFieldIdentifier.CLUSTER)
                                                 .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                                                 .build())
                                  .viewOperator(ViewIdOperator.IN)
                                  .values(Arrays.asList("cluster1"))
                                  .build()))
            .build());

    QLCEViewGroupBy groupBy = QLCEViewGroupBy.builder()
                                  .entityGroupBy(QLCEViewFieldInput.builder()
                                                     .fieldId(namespace.getFieldId())
                                                     .fieldName(namespace.getFieldName())
                                                     .identifier(ViewFieldIdentifier.CLUSTER)
                                                     .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                                                     .build())
                                  .build();

    QLCEViewFilter clusterFilter = QLCEViewFilter.builder()
                                       .field(QLCEViewFieldInput.builder()
                                                  .fieldId(namespace.getFieldId())
                                                  .fieldName(namespace.getFieldName())
                                                  .identifier(ViewFieldIdentifier.CLUSTER)
                                                  .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                                                  .build())
                                       .operator(QLCEViewFilterOperator.NOT_IN)
                                       .values(new String[] {"dummyCluster"})
                                       .build();

    QLCEViewAggregation costAgg = QLCEViewAggregation.builder()
                                      .operationType(QLCEViewAggregateOperation.SUM)
                                      .columnName(ViewsMetaDataFields.COST.getFieldName())
                                      .build();
    QLCEViewAggregation maxStartTimeAgg = QLCEViewAggregation.builder()
                                              .operationType(QLCEViewAggregateOperation.MAX)
                                              .columnName(ViewsMetaDataFields.START_TIME.getFieldName())
                                              .build();
    QLCEViewAggregation minStartTimeAgg = QLCEViewAggregation.builder()
                                              .operationType(QLCEViewAggregateOperation.MIN)
                                              .columnName(ViewsMetaDataFields.START_TIME.getFieldName())
                                              .build();

    QLCEViewSortCriteria costAscSort =
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.ASCENDING).sortType(QLCEViewSortType.COST).build();
    QLCEViewSortCriteria startTimeDescSort =
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.TIME).build();

    SelectQuery selectQuery = viewsQueryBuilder.getQuery(viewRules, Collections.singletonList(clusterFilter),
        Arrays.asList(startTimeFilter, endTimeFilter), Collections.singletonList(groupBy),
        Arrays.asList(costAgg, minStartTimeAgg, maxStartTimeAgg), Arrays.asList(costAscSort, startTimeDescSort),
        "TableName", Collections.emptyList());
    assertThat(selectQuery.toString()).contains("GROUP BY namespace");
    assertThat(selectQuery.toString())
        .contains("(clusterName IN ('cluster1') ) AND (namespace NOT IN ('dummyCluster') )");
    assertThat(selectQuery.toString())
        .contains(
            "(((instancetype IS NULL) OR (instancetype IN ('K8S_POD','K8S_POD_FARGATE','ECS_TASK_FARGATE','ECS_TASK_EC2') )) AND (clusterName IN ('cluster1') )");
    assertThat(selectQuery.toString())
        .contains(
            "SELECT namespace,SUM(cost) AS cost,MIN(startTime) AS startTime_MIN,MAX(startTime) AS startTime_MAX FROM TableName");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testSqlAndOrCondition() {
    List<Condition> conditionList = new ArrayList<>();
    Condition condition = UnaryCondition.isNotNull(new CustomSql(awsFilter));
    conditionList.add(condition);
    conditionList.add(condition);
    Condition sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    Condition sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(sqlAndCondition.toString())
        .isEqualTo("((awsService IS NOT NULL IS NOT NULL) AND (awsService IS NOT NULL IS NOT NULL))");
    assertThat(sqlOrCondition.toString())
        .isEqualTo("((awsService IS NOT NULL IS NOT NULL) OR (awsService IS NOT NULL IS NOT NULL))");

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(2);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(2);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(3);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(3);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(4);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(4);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(5);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(5);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(6);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(6);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(7);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(7);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(8);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(8);

    conditionList.add(condition);
    sqlAndCondition = viewsQueryBuilder.getSqlAndCondition(conditionList);
    sqlOrCondition = viewsQueryBuilder.getSqlOrCondition(conditionList);
    assertThat(StringUtils.countMatches(sqlAndCondition.toString(), "AND")).isEqualTo(9);
    assertThat(StringUtils.countMatches(sqlOrCondition.toString(), "OR")).isEqualTo(9);
  }
}
