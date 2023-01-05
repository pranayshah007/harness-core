/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;
import static software.wings.graphql.utils.nameservice.NameService.pipeline;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPipeline;
import software.wings.graphql.schema.type.QLPipeline.QLPipelineBuilder;
import software.wings.graphql.schema.type.QLPipelineConnection;
import software.wings.graphql.schema.type.QLPipelineConnection.QLPipelineConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class PipelineConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLPipelineFilter, QLNoOpSortCriteria, QLPipelineConnection> {
  @Inject PipelineQueryHelper pipelineQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionType.PIPELINE, action = Action.READ)
  public QLPipelineConnection fetchConnection(List<QLPipelineFilter> pipelineFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Pipeline> query = populateFilters(wingsPersistence, pipelineFilters, Pipeline.class, true);
    query.order(Sort.descending(PipelineKeys.createdAt));

    QLPipelineConnectionBuilder connectionBuilder = QLPipelineConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, pipeline -> {
      QLPipelineBuilder builder = QLPipeline.builder();
      PipelineController.populatePipeline(pipeline, builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLPipelineFilter> filters, Query query) {
    pipelineQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLPipelineFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLPipelineFilter.builder().application(idFilter).build();
    } else if (pipeline.equals(key)) {
      return QLPipelineFilter.builder().pipeline(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
