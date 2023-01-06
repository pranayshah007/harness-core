/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.tag;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.user.UserController;
import software.wings.graphql.schema.type.QLTagEntity.QLTagEntityBuilder;
import software.wings.graphql.schema.type.QLTagLink.QLTagLinkBuilder;
import software.wings.graphql.schema.type.aggregation.QLEntityType;
import software.wings.graphql.schema.type.aggregation.QLEntityTypeFilter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagEntityFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.tag.QLTagUseFilter;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 09/08/2019
 */
@Slf4j
@Singleton
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class TagHelper {
  private static final String INVALID_ENTITY_ERROR = "Invalid entityId: %s for entityType: %s";
  private static final String INVALID_ENTITY_TYPE = "Invalid entity type: %s";

  @Inject protected HarnessTagService tagService;
  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected HPersistence persistence;
  @Inject protected DataFetcherUtils utils;
  @Inject protected WingsPersistence wingsPersistence;

  // Returns set of all unique entity ids that match the tags for given entity type
  public Set<String> getEntityIdsFromTags(String accountId, List<QLTagInput> tags, EntityType entityType) {
    if (isNotEmpty(tags)) {
      Set<String> entityIds = new HashSet<>();
      tags.forEach(tag -> {
        Set<String> entityIdsForTag =
            tagService.getEntityIdsWithTag(accountId, tag.getName(), entityType, tag.getValue());
        if (isNotEmpty(entityIdsForTag)) {
          entityIds.addAll(entityIdsForTag);
        }
      });
      return entityIds;
    }
    return null;
  }

  public Set<String> getWorkExecutionsWithTags(String accountId, List<QLTagInput> tags) {
    if (isNotEmpty(tags)) {
      Set<String> entityIds = new HashSet<>();
      tags.forEach(tag -> {
        Set<String> workflowExecutionsWithTags =
            workflowExecutionService.getWorkflowExecutionsWithTag(accountId, tag.getName(), tag.getValue());
        if (isNotEmpty(workflowExecutionsWithTags)) {
          entityIds.addAll(workflowExecutionsWithTags);
        }
      });
      return entityIds;
    }
    return null;
  }

  public String validateAndFetchAppId(String entityId, QLEntityType entityType) {
    String appId = "";

    switch (entityType) {
      case APPLICATION:
        Application application = persistence.get(Application.class, entityId);
        if (application != null) {
          appId = application.getUuid();
        }
        break;
      case SERVICE:
        Service service = persistence.get(Service.class, entityId);
        if (service != null) {
          appId = service.getAppId();
        }
        break;
      case ENVIRONMENT:
        Environment environment = persistence.get(Environment.class, entityId);
        if (environment != null) {
          appId = environment.getAppId();
        }
        break;
      case WORKFLOW:
        Workflow workflow = persistence.get(Workflow.class, entityId);
        if (workflow != null) {
          appId = workflow.getAppId();
        }
        break;
      case PIPELINE:
        Pipeline pipeline = persistence.get(Pipeline.class, entityId);
        if (pipeline != null) {
          appId = pipeline.getAppId();
        }
        break;
      case PROVISIONER:
        InfrastructureProvisioner infrastructureProvisioner =
            persistence.get(InfrastructureProvisioner.class, entityId);
        if (infrastructureProvisioner != null) {
          appId = infrastructureProvisioner.getAppId();
        }
        break;
      default:
        throw new InvalidRequestException(format(INVALID_ENTITY_TYPE, entityType.name()));
    }

    if (EmptyPredicate.isEmpty(appId)) {
      throw new InvalidRequestException(format(INVALID_ENTITY_ERROR, entityId, entityType.name()));
    }

    return appId;
  }

  public void setUsageQuery(List<QLTagUseFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<HarnessTagLink>> field;

      if (filter.getTagName() != null) {
        field = query.field("key");
        QLIdFilter keyFilter = filter.getTagName();
        utils.setIdFilter(field, keyFilter);
      }

      if (filter.getEntityType() != null) {
        field = query.field("entityType");
        QLEntityTypeFilter entityTypeFilter = filter.getEntityType();
        utils.setEnumFilter(field, entityTypeFilter);
      }

      if (filter.getTagValue() != null) {
        field = query.field("value");
        QLIdFilter keyFilter = filter.getTagValue();
        utils.setIdFilter(field, keyFilter);
      }
    });
  }

  public EntityType getEntityType(QLApplicationTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      default:
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }

  public QLTagEntityBuilder populateTagEntity(HarnessTag tag, QLTagEntityBuilder builder) {
    return builder.id(tag.getUuid())
        .name(tag.getKey())
        .createdAt(tag.getCreatedAt())
        .createdBy(UserController.populateUser(tag.getCreatedBy()));
  }

  public QLTagLinkBuilder populateTagLink(HarnessTagLink tagLink, QLTagLinkBuilder builder) {
    return builder.entityId(tagLink.getEntityId())
        .entityType(QLEntityType.valueOf(tagLink.getEntityType().name()))
        .name(tagLink.getKey())
        .value(tagLink.getValue())
        .appId(tagLink.getAppId());
  }

  // filtering harness tags using id and key value
  public void setTagQuery(List<QLTagEntityFilter> filters, Query<HarnessTag> query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<HarnessTag>> field;

      if (filter.getTagId() != null) {
        field = query.field("_id");
        QLIdFilter applicationFilter = filter.getTagId();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getTagName() != null) {
        field = query.field("key");
        QLIdFilter nameFilter = filter.getTagName();
        utils.setIdFilter(field, nameFilter);
      }
    });
  }
}
