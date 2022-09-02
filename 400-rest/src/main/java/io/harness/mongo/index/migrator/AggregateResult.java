/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index.migrator;

import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import lombok.Data;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@StoreIn(DbAliases.HARNESS)
@Entity(noClassnameStored = true)
public class AggregateResult implements PersistentEntity {
  @Id private AccountAndName _id;
  private Integer count;
}
