/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.model;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.ALL)
@Entity(value = "genericEvent", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class GenericEvent extends Queuable {
  private Event event;
}
