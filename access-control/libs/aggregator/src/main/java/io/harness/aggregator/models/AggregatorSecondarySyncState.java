/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.models;

import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import io.harness.accesscontrol.aggregator.api.SecondarySyncStatus;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "AggregatorSecondarySyncStateKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "aggregatorSecondarySyncState", noClassnameStored = true)
@Document("aggregatorSecondarySyncState")
@TypeAlias("aggregatorSecondarySyncState")
public class AggregatorSecondarySyncState {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @FdUniqueIndex private String identifier;
  private SecondarySyncStatus secondarySyncStatus;

  @CreatedDate private Long createdAt;
  @LastModifiedDate private Long lastModifiedAt;
  @Version private Long version;
}
