/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.infraDefinition;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionFilter;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionTagFilter;
import software.wings.graphql.schema.type.QLInfrastructureDefinitionTagType;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLStringFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InfrastructureDefinitionQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setQuery(List<QLInfrastructureDefinitionFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<InfrastructureDefinition>> field;

      if (filter.getEnvironment() != null) {
        field = query.field(InfrastructureDefinitionKeys.envId);
        QLIdFilter environmentFilter = filter.getEnvironment();
        utils.setIdFilter(field, environmentFilter);
      }

      if (filter.getInfrastructureDefinition() != null) {
        field = query.field(InfrastructureDefinitionKeys.uuid);
        QLIdFilter infrastructureDefinitionFilter = filter.getInfrastructureDefinition();
        utils.setIdFilter(field, infrastructureDefinitionFilter);
      }

      if (filter.getDeploymentType() != null) {
        field = query.field(InfrastructureDefinitionKeys.deploymentType);
        QLStringFilter deploymentTypeFilter = filter.getDeploymentType();
        utils.setStringFilter(field, deploymentTypeFilter);
      }

      if (filter.getTag() != null) {
        QLInfrastructureDefinitionTagFilter triggerTagFilter = filter.getTag();
        List<QLTagInput> tags = triggerTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(triggerTagFilter.getEntityType()));
        switch (triggerTagFilter.getEntityType()) {
          case ENVIRONMENT:
            query.field("envId").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", triggerTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLInfrastructureDefinitionTagType entityType) {
    switch (entityType) {
      case ENVIRONMENT:
        return EntityType.ENVIRONMENT;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
