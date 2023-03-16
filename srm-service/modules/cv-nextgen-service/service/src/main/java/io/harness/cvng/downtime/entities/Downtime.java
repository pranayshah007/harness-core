/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.cvng.downtime.beans.DowntimeCategory;
import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeRecurrence;
import io.harness.cvng.downtime.beans.DowntimeScope;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntitiesRule;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DowntimeKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.CVNG)
@Entity(value = "downtime", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class Downtime
    implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware, CreatedByAware, UpdatedByAware {
  @Id private String uuid;
  @NotNull String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @NotNull String identifier;
  @NotNull String name;
  String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;
  @NotNull DowntimeCategory category;
  @NotNull DowntimeScope scope;
  @NotNull DowntimeType type;
  @NotNull String timezone;
  @NotNull DowntimeDetails downtimeDetails;
  private EntitiesRule entitiesRule;

  private boolean enabled;

  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  private long createdAt;
  private long lastUpdatedAt;

  @Data
  @SuperBuilder
  @EqualsAndHashCode
  public abstract static class DowntimeDetails {
    long startTime;

    public abstract DowntimeType getType();
  }

  @Data
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public abstract static class OnetimeDowntimeDetails extends DowntimeDetails {
    private final DowntimeType type = DowntimeType.ONE_TIME;
    public abstract OnetimeDowntimeType getOnetimeDowntimeType();
  }

  @Value
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public static class OnetimeDurationBased extends OnetimeDowntimeDetails {
    @NotNull DowntimeDuration downtimeDuration;
    OnetimeDowntimeType onetimeDowntimeType = OnetimeDowntimeType.DURATION;
  }

  @Value
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public static class EndTimeBased extends OnetimeDowntimeDetails {
    @NotNull long endTime;
    OnetimeDowntimeType onetimeDowntimeType = OnetimeDowntimeType.END_TIME;
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class RecurringDowntimeDetails extends DowntimeDetails {
    long recurrenceEndTime;
    @ApiModelProperty(required = true) @NotNull DowntimeDuration downtimeDuration;
    @ApiModelProperty(required = true) @NotNull DowntimeRecurrence downtimeRecurrence;
    private final DowntimeType type = DowntimeType.RECURRING;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("downtime_acc_org_proj_identifier_idx")
                 .field(DowntimeKeys.accountId)
                 .field(DowntimeKeys.orgIdentifier)
                 .field(DowntimeKeys.projectIdentifier)
                 .field(DowntimeKeys.identifier)
                 .build())
        .build();
  }
}
