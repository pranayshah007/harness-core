/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ServiceSecretKeyKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "serviceSecrets", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ServiceSecretKey implements PersistentEntity, CreatedAtAware, UpdatedAtAware {
  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  private String serviceSecret;

  @FdUniqueIndex private ServiceType serviceType;

  public enum ServiceType { LEARNING_ENGINE }

  // add version in the end
  public enum ServiceApiVersion { V1 }
}
