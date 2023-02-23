/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.tag;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTag.HarnessTagKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLTagConnection;
import software.wings.graphql.schema.type.QLTagConnection.QLTagConnectionBuilder;
import software.wings.graphql.schema.type.QLTagEntity;
import software.wings.graphql.schema.type.QLTagEntity.QLTagEntityBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.tag.QLTagEntityFilter;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class TagConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLTagEntityFilter, QLNoOpSortCriteria, QLTagConnection> {
  @Inject TagHelper tagHelper;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  public QLTagConnection fetchConnection(List<QLTagEntityFilter> tagEntityFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<HarnessTag> query = populateFilters(wingsPersistence, tagEntityFilters, HarnessTag.class, true);
    query.order(Sort.descending(HarnessTagKeys.createdAt));

    QLTagConnectionBuilder connectionBuilder = QLTagConnection.builder();

    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, tag -> {
      QLTagEntityBuilder builder = QLTagEntity.builder();
      tagHelper.populateTagEntity(tag, builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected QLTagEntityFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (NameService.tag.equals(key)) {
      return QLTagEntityFilter.builder().tagName(idFilter).build();
    }
    throw new InvalidRequestException("Unsupported field " + key + " while generating filter");
  }

  @Override
  protected void populateFilters(List<QLTagEntityFilter> filters, Query query) {
    tagHelper.setTagQuery(filters, query, getAccountId());
  }
}
