/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.yaml.BaseYamlWithType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "CgEventConfigKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "cgEventConfig", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class CgEventConfig
    extends EventConfig implements PersistentEntity, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware,
                                   ApplicationAccess, NameAccess, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(CgEventConfigKeys.appId)
                 .field(CgEventConfigKeys.name)
                 .build())
        .build();
  }

  @Trimmed(message = "Event Config Name should not contain leading and trailing spaces")
  @NotBlank
  @EntityName
  private String name;
  @NotNull private WebHookEventConfig config;
  private CgEventRule rule;
  @FdIndex private String accountId;
  private List<String> delegateSelectors;
  private boolean enabled;

  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @JsonIgnore @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  private transient String summary;

  public String getSummary() {
    StringBuilder builder = new StringBuilder();
    if (CgEventRule.CgRuleType.ALL.equals(rule.getType())) {
      return builder.append("Send everything").toString();
    }

    if (CgEventRule.CgRuleType.PIPELINE.equals(rule.getType())) {
      return getPipelineSummary(rule.getPipelineRule());
    }

    if (CgEventRule.CgRuleType.WORKFLOW.equals(rule.getType())) {
      return getWorkflowSummary(rule.getWorkflowRule());
    }
    return builder.toString();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getAccountId() {
    return accountId;
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public long getCreatedAt() {
    return createdAt;
  }

  @Override
  public void setCreatedBy(EmbeddedUser createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public EmbeddedUser getCreatedBy() {
    return createdBy;
  }

  @Override
  public void setLastUpdatedAt(long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  @Override
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  @Override
  public void setLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  @Override
  public EmbeddedUser getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  @UtilityClass
  public static final class CgEventConfigKeys {
    public static final String uuid = "uuid";
  }

  private static String getPipelineSummary(CgEventRule.PipelineRule pipelineRule) {
    StringBuilder builder = new StringBuilder();
    if (pipelineRule.isAllEvents() && pipelineRule.isAllPipelines()) {
      return builder.append("All events for all Pipelines").toString();
    }
    if (!pipelineRule.isAllEvents() && pipelineRule.isAllPipelines()) {
      String events = StringUtils.join(pipelineRule.getEvents(), ", ");
      return builder.append("Events ").append(events).append(" for all Pipelines").toString();
    }
    if (pipelineRule.isAllEvents() && !pipelineRule.isAllPipelines()) {
      return builder.append("All events for select ")
          .append(pipelineRule.getPipelineIds().size())
          .append(" Pipeline(s)")
          .toString();
    }
    String events = StringUtils.join(pipelineRule.getEvents(), ", ");
    return builder.append("Events ")
        .append(events)
        .append(" ")
        .append(pipelineRule.getPipelineIds().size())
        .append(" Pipeline(s)")
        .toString();
  }

  private static String getWorkflowSummary(CgEventRule.WorkflowRule workflowRule) {
    StringBuilder builder = new StringBuilder();
    if (workflowRule.isAllEvents() && workflowRule.isAllWorkflows()) {
      return builder.append("All events for all Workflows").toString();
    }
    if (!workflowRule.isAllEvents() && workflowRule.isAllWorkflows()) {
      String events = StringUtils.join(workflowRule.getEvents(), ", ");
      return builder.append("Events ").append(events).append(" for all Workflows").toString();
    }
    if (workflowRule.isAllEvents() && !workflowRule.isAllWorkflows()) {
      return builder.append("All events for select ")
          .append(workflowRule.getWorkflowIds().size())
          .append(" Workflow(s)")
          .toString();
    }
    String events = StringUtils.join(workflowRule.getEvents(), ", ");
    return builder.append("Events ")
        .append(events)
        .append(" ")
        .append(workflowRule.getWorkflowIds().size())
        .append(" Workflow(s)")
        .toString();
  }
  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static final class Yaml extends BaseYamlWithType {
    private boolean enabled;
    private String type;
    private List<String> delegateSelectors;
    private WebHookEventConfig.Yaml webhookEventConfiguration;
    private CgEventRule.Yaml eventRule;

    @lombok.Builder
    public Yaml(String type, boolean enabled, CgEventRule.Yaml cgEventRule, List<String> delegateSelectors,
        WebHookEventConfig.Yaml webhookEventConfig) {
      this.eventRule = cgEventRule;
      this.type = type;
      this.webhookEventConfiguration = webhookEventConfig;
      this.delegateSelectors = delegateSelectors;
      this.enabled = enabled;
    }
  }
}
