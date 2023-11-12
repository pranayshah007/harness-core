/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.event;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn("harnesssto")
@Entity(value = "stoAccountDataStatus", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "STOAccountDataStatusKeys")
@Document("stoAccountDataStatus")
@HarnessEntity(exportable = true)
@TypeAlias("stoAccountDataStatus")
public class STOAccountDataStatus {
  @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  Boolean deleted;
  Long lastSent;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("accountIdIdx").field(STOAccountDataStatusKeys.accountId).build())

        .build();
  }
}