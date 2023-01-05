/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.queue;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.context.GlobalContext;
import io.harness.context.GlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PrePersist;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "QueuableKeys")
public abstract class Queuable implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("next4")
                 .field(QueuableKeys.topic)
                 .field(QueuableKeys.earliestGet)
                 .build())
        .build();
  }

  @Getter @Setter @Id private String id;

  @Getter
  @Setter
  // Old earliestGet is an indication of event that is abandoned. No need to keep it around.
  @FdTtlIndex(24 * 60 * 60)
  private Date earliestGet = new Date();

  @Getter @Setter private int retries;
  @Getter @Setter private String topic;
  @Getter @Setter private GlobalContext globalContext;

  protected Queuable() {}

  protected Queuable(Date earliestGet) {
    this.earliestGet = earliestGet;
  }

  @PrePersist
  public void onUpdate() {
    if (id == null) {
      id = generateUuid();
    }
  }

  protected void updateGlobalContext(GlobalContextData globalContextData) {
    if (globalContextData != null) {
      GlobalContextManager.upsertGlobalContextRecord(globalContextData);
    }
  }
}
