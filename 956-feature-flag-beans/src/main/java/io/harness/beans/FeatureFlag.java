/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.mongo.index.FdIndex;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@Entity(value = "featureFlag", noClassnameStored = true)
@HarnessEntity(exportable = true)
@JsonIgnoreProperties({"obsolete", "accountIds"})
@FieldNameConstants(innerTypeName = "FeatureFlagKeys")
public final class FeatureFlag implements PersistentEntity, UuidAware, UpdatedAtAware {
  @Id private String uuid;

  @FdIndex private String name;
  public enum Scope {
    GLOBAL,
    PER_ACCOUNT,
  }

  private boolean enabled;
  private boolean obsolete;
  private Set<String> accountIds;

  private long lastUpdatedAt;
}
