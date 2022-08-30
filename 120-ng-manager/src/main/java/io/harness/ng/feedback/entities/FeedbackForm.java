/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.feedback.entities;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.ModuleType;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "FeedbackFormKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "feedbackForms", noClassnameStored = true)
@Document("feedbackForms")
@Persistent
@OwnedBy(GTM)
public class FeedbackForm implements PersistentEntity, NGAccountAccess {
  @Id @org.mongodb.morphia.annotations.Id protected String id;
  @NotEmpty @Trimmed protected String accountIdentifier;
  @NotEmpty protected String email;
  @NotEmpty protected ModuleType moduleType;
  protected Integer score;
  @Size(max = 500) protected String suggestion;
  @CreatedBy protected EmbeddedUser createdBy;
  @CreatedDate protected Long createdAt;
}
