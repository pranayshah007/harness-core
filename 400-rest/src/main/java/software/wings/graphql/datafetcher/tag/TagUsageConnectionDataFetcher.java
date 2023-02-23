/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.tag;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.HarnessTag.HarnessTagKeys;
import software.wings.beans.HarnessTagLink;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLTagLink;
import software.wings.graphql.schema.type.QLTagLink.QLTagLinkBuilder;
import software.wings.graphql.schema.type.QLTagUsageConnection;
import software.wings.graphql.schema.type.QLTagUsageConnection.QLTagUsageConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.tag.QLTagUseFilter;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.HarnessTagService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class TagUsageConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLTagUseFilter, QLNoOpSortCriteria, QLTagUsageConnection> {
  @Inject TagHelper tagHelper;
  @Inject HarnessTagService harnessTagService;

  @Override
  protected QLTagUseFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();

    if (NameService.tag.equals(key)) {
      return QLTagUseFilter.builder().tagName(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }

  @Override
  @AuthRule(permissionType = LOGGED_IN, action = READ)
  protected QLTagUsageConnection fetchConnection(
      List<QLTagUseFilter> filters, QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<HarnessTagLink> query = populateFilters(wingsPersistence, filters, HarnessTagLink.class, true);
    query.order(Sort.descending(HarnessTagKeys.createdAt));

    QLTagUsageConnectionBuilder qlTagUsageConnectionBuilder = QLTagUsageConnection.builder();

    qlTagUsageConnectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, tagLink -> {
      try {
        harnessTagService.validateTagResourceAccess(
            tagLink.getAppId(), tagLink.getAccountId(), tagLink.getEntityId(), tagLink.getEntityType(), READ);
        QLTagLinkBuilder builder = QLTagLink.builder();
        tagHelper.populateTagLink(tagLink, builder);
        qlTagUsageConnectionBuilder.node(builder.build());
      } catch (Exception ex) {
        // Exception is thrown if the user does not have permissions on the entity
      }
    }));

    return qlTagUsageConnectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLTagUseFilter> filters, Query query) {
    tagHelper.setUsageQuery(filters, query, getAccountId());
  }
}
