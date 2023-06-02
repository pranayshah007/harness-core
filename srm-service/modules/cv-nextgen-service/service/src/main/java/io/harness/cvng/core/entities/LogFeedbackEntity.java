/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.ticket.entities.Ticket;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "LogFeedbackKeys")
@EqualsAndHashCode
@StoreIn(DbAliases.CVNG)
@Entity(value = "logFeedbackEntity", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class LogFeedbackEntity implements PersistentEntity {
  @Id private String feedbackId;
  private String sampleMessage;
  private String description;
  private String feedbackScore;
  private String serviceIdentifier;
  private String environmentIdentifier;
  @FdIndex private String accountIdentifier;
  @FdIndex private String projectIdentifier;
  @FdIndex private String orgIdentifier;
  private String createdByUser;
  private String updatedByUser;
  private long createdAt;
  private long lastUpdatedAt;
  private Ticket ticket;
}
