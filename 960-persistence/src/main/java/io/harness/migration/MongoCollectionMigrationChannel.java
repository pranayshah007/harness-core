/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migration;

import io.harness.persistence.store.Store;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MongoCollectionMigrationChannel implements MigrationChannel {
  private Store store;
  private String collection;

  @Override
  public String getId() {
    return "mongodb://store/" + store.getName() + "/collection/" + collection;
  }
}
