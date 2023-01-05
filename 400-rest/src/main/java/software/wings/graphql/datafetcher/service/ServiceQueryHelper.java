/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentType;
import software.wings.graphql.schema.type.aggregation.service.QLDeploymentTypeFilter;
import software.wings.graphql.schema.type.aggregation.service.QLServiceFilter;
import software.wings.graphql.schema.type.aggregation.service.QLServiceTagFilter;
import software.wings.graphql.schema.type.aggregation.service.QLServiceTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 07/12/19
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ServiceQueryHelper {
  @Inject private DataFetcherUtils utils;
  @Inject private TagHelper tagHelper;

  public void setQuery(List<QLServiceFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Service>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getService() != null) {
        field = query.field("_id");
        QLIdFilter serviceFilter = filter.getService();
        utils.setIdFilter(field, serviceFilter);
      }

      if (filter.getDeploymentType() != null) {
        field = query.field("deploymentType");
        QLDeploymentTypeFilter deploymentTypeFilter = filter.getDeploymentType();
        QLDeploymentTypeFilter deploymentTypeFilterWithoutNull =
            QLDeploymentTypeFilter.builder()
                .operator(deploymentTypeFilter.getOperator())
                .values(Arrays.stream(deploymentTypeFilter.getValues())
                            .filter(Objects::nonNull)
                            .toArray(QLDeploymentType[] ::new))
                .build();
        utils.setEnumFilter(field, deploymentTypeFilterWithoutNull);
      }

      if (filter.getTag() != null) {
        QLServiceTagFilter serviceTagFilter = filter.getTag();
        List<QLTagInput> tags = serviceTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(serviceTagFilter.getEntityType()));
        switch (serviceTagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          case SERVICE:
            query.field("_id").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", serviceTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLServiceTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case SERVICE:
        return EntityType.SERVICE;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
