/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CEReportScheduleKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "ceReportSchedule", noClassnameStored = true)
@OwnedBy(HarnessTeam.CE)
@Schema(
    description = "Cloud Cost Report Schedule contains definition of 'how often' and 'to whom' the Report will be sent")
public final class CEReportSchedule implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware,
                                               AccountAccess, CreatedByAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_enabled_type")
                 .field(CEReportScheduleKeys.accountId)
                 .field(CEReportScheduleKeys.enabled)
                 .field(CEReportScheduleKeys.name)
                 .field(CEReportScheduleKeys.viewsId)
                 .build())
        .build();
  }

  @Id String uuid;
  @NotEmpty(message = "Name for report schedule must not be empty")
  @Size(min = 1, max = 80, message = ": for report schedule name must be between 1 and 80 characters long")
  String name;
  @Builder.Default boolean enabled = true;
  @Size(max = 100, message = ": for report schedule description must be between 0 and 100 characters long")
  @Builder.Default
  String description = "";
  @NotNull @Size(min = 1, max = 1, message = ": for report schedule, one viewId is needed") String[] viewsId;
  @NotEmpty(message = "report schedule cron must not be empty") String userCron;
  @Size(max = 50, message = ": for report schedule maximum 50 recipients are allowed")
  @NotEmpty(message = "At least one email recipient must be provided")
  String[] recipients;
  String accountId;
  long createdAt;
  long lastUpdatedAt;
  @Builder.Default String userCronTimeZone = "UTC";
  EmbeddedUser createdBy;
  EmbeddedUser lastUpdatedBy;
  Date nextExecution;

  public CEReportSchedule toDTO() {
    return CEReportSchedule.builder()
        .uuid(getUuid())
        .name(getName())
        .description(getDescription())
        .viewsId(getViewsId())
        .userCron(getUserCron())
        .recipients(getRecipients())
        .accountId(getAccountId())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .userCronTimeZone(getUserCronTimeZone())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .nextExecution(getNextExecution())
        .build();
  }
}
