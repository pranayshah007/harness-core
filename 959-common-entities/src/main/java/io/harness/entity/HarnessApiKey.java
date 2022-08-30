/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.ClientType;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@NoArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "harnessApiKeys", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "HarnessApiKeyKeys")
public class HarnessApiKey implements PersistentEntity, UuidAware {
  @Id private String uuid;
  @NotEmpty private byte[] encryptedKey;
  @FdIndex @NotEmpty private ClientType clientType;

  @Builder
  private HarnessApiKey(String uuid, byte[] encryptedKey, ClientType clientType) {
    this.uuid = uuid;
    this.encryptedKey = encryptedKey;
    this.clientType = clientType;
  }

  @Override
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  @Override
  public String getUuid() {
    return uuid;
  }
}
