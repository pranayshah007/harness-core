/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.steps;

import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import lombok.Builder;
import lombok.Value;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Value
@Builder
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testTransport", noClassnameStored = true)
public class TestTransportEntity implements PersistentEntity {
  @Id private String uuid;
}
