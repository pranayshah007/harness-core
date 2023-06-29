/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "metricPacks", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "MetricPackKeys")
@HarnessEntity(exportable = true)
public final class MetricPack implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("idx")
                 .unique(true)
                 .field(MetricPackKeys.accountId)
                 .field(MetricPackKeys.orgIdentifier)
                 .field(MetricPackKeys.projectIdentifier)
                 .field(MetricPackKeys.dataSourceType)
                 .field(MetricPackKeys.identifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  private String accountId;
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotNull private DataSourceType dataSourceType;
  @Trimmed @NotEmpty private String identifier;
  @NotNull private CVMonitoringCategory category;
  @NotEmpty private Set<MetricDefinition> metrics;
  private String dataCollectionDsl;
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getDataCollectionDsl() {
    return dataCollectionDsl;
  }

  public void addToMetrics(MetricDefinition metricDefinition) {
    if (this.metrics == null) {
      this.metrics = new HashSet<>();
    }
    this.metrics.add(metricDefinition);
  }

  public Set<MetricDefinition> getMetrics() {
    if (this.metrics == null) {
      return Collections.emptySet();
    }
    return metrics;
  }

  public io.harness.cvng.beans.MetricPackDTO toDTO() {
    return io.harness.cvng.beans.MetricPackDTO.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .dataSourceType(getDataSourceType())
        .identifier(getIdentifier())
        .category(getCategory())
        .metrics(getMetrics().stream().map(MetricDefinition::toDTO).collect(Collectors.toSet()))
        .build();
  }

  @Data
  @Builder
  @EqualsAndHashCode(of = {"name"})
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricDefinition {
    @Trimmed @NotEmpty private String name;
    @NotEmpty private String identifier;
    @NotNull private TimeSeriesMetricType type;
    private String path;
    private String validationPath;
    private String responseJsonPath;
    private String validationResponseJsonPath;
    private boolean included;
    @Builder.Default private List<TimeSeriesThreshold> thresholds = new ArrayList<>();
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getPath() {
      return path;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getValidationPath() {
      return validationPath;
    }

    public io.harness.cvng.beans.MetricPackDTO.MetricDefinitionDTO toDTO() {
      return io.harness.cvng.beans.MetricPackDTO.MetricDefinitionDTO.builder()
          .name(name)
          .metricIdentifier(identifier)
          .path(path)
          .responseJsonPath(responseJsonPath)
          .validationResponseJsonPath(validationResponseJsonPath)
          .type(type)
          .validationPath(validationPath)
          .included(included)
          .thresholds(isEmpty(thresholds)
                  ? new ArrayList<>()
                  : thresholds.stream().map(TimeSeriesThreshold::toDTO).collect(Collectors.toList()))
          .build();
    }
  }
}
