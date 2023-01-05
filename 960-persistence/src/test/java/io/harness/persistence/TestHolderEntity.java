/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testHolder", noClassnameStored = true)
public class TestHolderEntity implements PersistentEntity {
  @Id private String uuid;
  MorphiaInterface morphiaObj;
}
