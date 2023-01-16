package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.core.entities.LogFeedbackEntity;
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
  String feedbackId;
  LogFeedbackEntity logFeedbackEntity;
  String createdByUser;
  String updatedByUser;
  String accountIdentifier;
  String projectIdentifier;
  String orgIdentifier;
}
