/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineFilter;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineTagFilter;
import software.wings.graphql.schema.type.aggregation.pipeline.QLPipelineTagType;
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
@Singleton
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class PipelineQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setQuery(List<QLPipelineFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Pipeline>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getPipeline() != null) {
        field = query.field("_id");
        QLIdFilter pipelineFilter = filter.getPipeline();
        utils.setIdFilter(field, pipelineFilter);
      }

      if (filter.getTag() != null) {
        QLPipelineTagFilter pipelineTagFilter = filter.getTag();
        List<QLTagInput> tags = pipelineTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(pipelineTagFilter.getEntityType()));
        switch (pipelineTagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          case PIPELINE:
            query.field("_id").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", pipelineTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLPipelineTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case PIPELINE:
        return EntityType.PIPELINE;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
