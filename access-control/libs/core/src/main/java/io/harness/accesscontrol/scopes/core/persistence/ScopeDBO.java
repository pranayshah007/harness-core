/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import static java.util.Optional.empty;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;

import dev.morphia.annotations.Entity;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "ScopeDBOKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "scopes", noClassnameStored = true)
@Document("scopes")
@TypeAlias("scopes")
public class ScopeDBO implements PersistentRegularIterable, AccessControlEntity {
  @Setter @Id @dev.morphia.annotations.Id String id;
  @FdUniqueIndex @NotEmpty final String identifier;
  @Setter String name;
  @Setter @CreatedDate Long createdAt;
  @Setter @LastModifiedDate Long lastModifiedAt;
  @Setter @CreatedBy EmbeddedUser createdBy;
  @Setter @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Setter @Version Long version;

  @FdIndex @Setter Long nextReconciliationIterationAt;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextReconciliationIterationAt;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ScopeDBOKeys.nextReconciliationIterationAt.equals(fieldName)) {
      nextReconciliationIterationAt = nextIteration;
    }
  }

  @Override
  public String getUuid() {
    return id;
  }

  @Override
  public Optional<String> getAccountId() {
    return empty();
  }
}
