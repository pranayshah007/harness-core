/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.beans.HeaderConfig;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.security.dto.Principal;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TriggerWebhookEventsKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "triggerWebhookEvents", noClassnameStored = true)
@Document("triggerWebhookEvents")
@TypeAlias("triggerWebhookEvents")
@HarnessEntity(exportable = true)
public class TriggerWebhookEvent implements PersistentEntity, UuidAccess, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;
  String payload;
  List<HeaderConfig> headers;
  String pipelineIdentifier;
  String triggerIdentifier;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String sourceRepoType;
  Principal principal;
  @Builder.Default boolean isSubscriptionConfirmation = Boolean.FALSE;

  @Setter @NonFinal @Builder.Default boolean processing = Boolean.FALSE;

  @Setter @NonFinal @Builder.Default Integer attemptCount = 0;

  @FdTtlIndex @Default Date validUntil = Date.from(OffsetDateTime.now().plusDays(7).toInstant());
  @CreatedDate Long createdAt;
  @NonFinal @Builder.Default Long nextIteration = 0L;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }
}
