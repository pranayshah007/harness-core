/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.core.beans.LogFeedback;
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
@FieldNameConstants(innerTypeName = "LogFeedbackHistoryKeys")
@EqualsAndHashCode
@StoreIn(DbAliases.CVNG)
@Entity(value = "logFeedbackHistory", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class LogFeedbackHistoryEntity implements PersistentEntity {
  @Id String historyId;
  @FdIndex String feedbackId;
  LogFeedback logFeedbackEntity;
  String createdByUser;
  String updatedByUser;
  @FdIndex String accountIdentifier;
  @FdIndex String projectIdentifier;
  @FdIndex String orgIdentifier;
}
