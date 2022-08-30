/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue;

import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import lombok.Value;
import org.mongodb.morphia.annotations.Entity;

@Value
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testTopicQueue", noClassnameStored = true)
public class TestTopicQueuableObject extends Queuable {
  private int data;

  public TestTopicQueuableObject(int data) {
    this.data = data;
  }
}
