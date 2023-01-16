/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import io.harness.beans.EmbeddedUser;

import dev.morphia.annotations.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "TestEntityCreatedLastUpdatedAwareKeys")
class TestEntityCreatedLastUpdatedAware
    implements PersistentEntity, UuidAccess, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware {
  @Id private String uuid;
  private long createdAt;
  private EmbeddedUser createdBy;
  private long lastUpdatedAt;
  private EmbeddedUser lastUpdatedBy;

  private String test;
}
