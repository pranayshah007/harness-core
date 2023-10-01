/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.KubernetesClusterActivityKeys;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.RelatedAppMonitoredService.ServiceEnvironmentKeys;
import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityDTO.VerificationJobRuntimeDetails;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.api.client.util.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;

@Data
@FieldNameConstants(innerTypeName = "ActivityKeys")
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "activities")
@HarnessEntity(exportable = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class Activity
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("deployment_query_idx")
                 .field(ActivityKeys.accountId)
                 .field(ActivityKeys.orgIdentifier)
                 .field(ActivityKeys.projectIdentifier)
                 .field(ActivityKeys.type)
                 .build(),
            CompoundMongoIndex.builder()
                .name("deployment_tag_idx")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.type)
                .field(ActivityKeys.verificationJobInstanceIds)
                .build(),
            CompoundMongoIndex.builder()
                .name("activities_query_index")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(ActivityKeys.activityStartTime)
                .build(),

            CompoundMongoIndex.builder()
                .name("change_event_app_service_query_indexv2")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(ActivityKeys.monitoredServiceIdentifier)
                .field(ActivityKeys.eventTime)
                .build(),

            CompoundMongoIndex.builder()
                .name("change_event_event_time_sort_query_indexv2")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(ActivityKeys.eventTime)
                .field(ActivityKeys.monitoredServiceIdentifier)
                .field(ActivityKeys.type)
                .build(),
            CompoundMongoIndex.builder()
                .name("change_event_event_time_sort_query_infra_service_indexv2")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(ActivityKeys.eventTime)
                .field(KubernetesClusterActivityKeys.relatedAppServices + "."
                    + ServiceEnvironmentKeys.monitoredServiceIdentifier)
                .field(ActivityKeys.type)
                .build(),
            CompoundMongoIndex.builder()
                .name("change_event_event_time_sort_query_type_index")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(ActivityKeys.eventTime)
                .field(ActivityKeys.type)
                .build(),
            CompoundMongoIndex.builder()
                .name("change_event_infra_service_query_indexv2")
                .field(ActivityKeys.accountId)
                .field(ActivityKeys.orgIdentifier)
                .field(ActivityKeys.projectIdentifier)
                .field(KubernetesClusterActivityKeys.relatedAppServices + "."
                    + ServiceEnvironmentKeys.monitoredServiceIdentifier)
                .field(ActivityKeys.eventTime)
                .sparse(true)
                .build())
        .build();
  }

  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;

  @NotNull private ActivityType type;
  @NotNull private String accountId;
  String monitoredServiceIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String orgIdentifier;
  private String activitySourceId;

  private String changeSourceIdentifier;
  private Instant eventTime;

  private String activityName;
  @Deprecated private List<VerificationJobRuntimeDetails> verificationJobRuntimeDetails;
  private List<VerificationJob> verificationJobs;

  @NotNull private Instant activityStartTime;
  private Instant activityEndTime;
  @FdIndex private List<String> verificationJobInstanceIds;
  private List<String> tags;
  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(2).toInstant());

  private ActivityVerificationSummary verificationSummary;

  @Builder.Default @FdIndex private ActivityVerificationStatus analysisStatus = ActivityVerificationStatus.IGNORED;

  @FdIndex private Long verificationIteration;

  public abstract ActivityType getType();

  public List<VerificationJob> getVerificationJobs() {
    if (verificationJobs == null) {
      verificationJobs = Collections.EMPTY_LIST;
    }
    return verificationJobs;
  }

  public abstract void fromDTO(ActivityDTO activityDTO);

  public abstract void fillInVerificationJobInstanceDetails(
      VerificationJobInstanceBuilder verificationJobInstanceBuilder);

  protected void addCommonFields(ActivityDTO activityDTO) {
    setAccountId(activityDTO.getAccountIdentifier());
    setProjectIdentifier(activityDTO.getProjectIdentifier());
    setOrgIdentifier(activityDTO.getOrgIdentifier());
    setActivityName(activityDTO.getName());
    setVerificationJobRuntimeDetails(activityDTO.getVerificationJobRuntimeDetails() == null
            ? null
            : new ArrayList<>(activityDTO.getVerificationJobRuntimeDetails()));
    setActivityStartTime(Instant.ofEpochMilli(activityDTO.getActivityStartTime()));
    setActivityEndTime(
        activityDTO.getActivityEndTime() != null ? Instant.ofEpochMilli(activityDTO.getActivityEndTime()) : null);
    setType(activityDTO.getType());
    setTags(activityDTO.getTags());
  }

  public abstract void validateActivityParams();

  public void validate() {
    Preconditions.checkNotNull(accountId, generateErrorMessageFromParam(ActivityKeys.accountId));
    Preconditions.checkNotNull(projectIdentifier, generateErrorMessageFromParam(ActivityKeys.projectIdentifier));
    Preconditions.checkNotNull(orgIdentifier, generateErrorMessageFromParam(ActivityKeys.orgIdentifier));
    Preconditions.checkNotNull(activityName, generateErrorMessageFromParam(ActivityKeys.activityName));
    Preconditions.checkNotNull(activityStartTime, generateErrorMessageFromParam(ActivityKeys.activityStartTime));
    Preconditions.checkNotNull(type, generateErrorMessageFromParam(ActivityKeys.type));
    this.validateActivityParams();
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (fieldName.equals(ActivityKeys.verificationIteration)) {
      this.verificationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (fieldName.equals(ActivityKeys.verificationIteration)) {
      return verificationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public abstract boolean deduplicateEvents();

  public abstract static class ActivityUpdatableEntity<T extends Activity, D extends Activity>
      implements UpdatableEntity<T, D> {
    public abstract Class getEntityClass();

    public abstract String getEntityKeyLongString(D activity);

    public String getEntityKeyString(D activity) {
      return UUID.nameUUIDFromBytes(getEntityKeyLongString(activity).getBytes(Charsets.UTF_8)).toString();
    }

    public Query<T> populateKeyQuery(Query<T> query, D activity) {
      return query.filter(ActivityKeys.accountId, activity.getAccountId())
          .filter(ActivityKeys.orgIdentifier, activity.getOrgIdentifier())
          .filter(ActivityKeys.projectIdentifier, activity.getProjectIdentifier())
          .filter(ActivityKeys.monitoredServiceIdentifier, activity.getMonitoredServiceIdentifier());
    }

    protected StringJoiner getKeyBuilder(Activity activity) {
      return new StringJoiner("+")
          .add(activity.getAccountId())
          .add(activity.getOrgIdentifier())
          .add(activity.getProjectIdentifier())
          .add(activity.getMonitoredServiceIdentifier())
          .add(activity.getType().name());
    }

    protected void setCommonUpdateOperations(UpdateOperations<T> updateOperations, D activity) {
      updateOperations.set(ActivityKeys.accountId, activity.getAccountId())
          .set(ActivityKeys.orgIdentifier, activity.getOrgIdentifier())
          .set(ActivityKeys.projectIdentifier, activity.getProjectIdentifier())
          .set(ActivityKeys.activityStartTime, activity.getActivityStartTime())
          .set(ActivityKeys.type, activity.getType());

      if (activity.getEventTime() != null) {
        updateOperations.set(ActivityKeys.eventTime, activity.getEventTime());
      }
      if (activity.getActivityEndTime() != null) {
        updateOperations.set(ActivityKeys.activityEndTime, activity.getActivityEndTime());
      }
      if (activity.getChangeSourceIdentifier() != null) {
        updateOperations.set(ActivityKeys.changeSourceIdentifier, activity.getChangeSourceIdentifier());
      }
      if (activity.getMonitoredServiceIdentifier() != null) {
        updateOperations.set(ActivityKeys.monitoredServiceIdentifier, activity.getMonitoredServiceIdentifier());
      }
      if (CollectionUtils.isNotEmpty(activity.getVerificationJobInstanceIds())) {
        updateOperations.addToSet(ActivityKeys.verificationJobInstanceIds, activity.getVerificationJobInstanceIds());
      }
      if (activity.getActivityName() != null) {
        updateOperations.set(ActivityKeys.activityName, activity.getActivityName());
      }
    }
  }
}
