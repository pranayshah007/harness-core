package io.harness.cvng.cdng.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
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
@Entity(value = "logFeedback", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class LogFeedbackEntity implements PersistentEntity {
  @Id private String feedbackId;
  private String description;
  private String feedbackScore;
  private String monitoredServiceIdentifier;
  private String accountIdentifier;
  private String orgIdentifier;
  private String createdByUser;
  private String updatedByUser;
  private long createdAt;
  private long lastUpdatedAt;
}
