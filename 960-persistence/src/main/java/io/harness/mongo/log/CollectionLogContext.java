/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.log;

import io.harness.logging.AutoLogContext;

public class CollectionLogContext extends AutoLogContext {
  public static final String ID = "collectionName";

  public CollectionLogContext(String collectionName, OverrideBehavior behavior) {
    super(ID, collectionName, behavior);
  }
}
