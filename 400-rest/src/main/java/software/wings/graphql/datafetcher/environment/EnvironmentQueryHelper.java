/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.environment;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTagFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTagType;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 07/12/19
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class EnvironmentQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setQuery(List<QLEnvironmentFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Environment>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getEnvironment() != null) {
        field = query.field("_id");
        QLIdFilter environmentFilter = filter.getEnvironment();
        utils.setIdFilter(field, environmentFilter);
      }

      if (filter.getEnvironmentType() != null) {
        field = query.field("environmentType");
        QLEnvironmentTypeFilter envTypeFilter = filter.getEnvironmentType();
        utils.setEnumFilter(field, envTypeFilter);
      }

      if (filter.getTag() != null) {
        QLEnvironmentTagFilter environmentTagFilter = filter.getTag();
        List<QLTagInput> tags = environmentTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(environmentTagFilter.getEntityType()));
        switch (environmentTagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", environmentTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLEnvironmentTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
