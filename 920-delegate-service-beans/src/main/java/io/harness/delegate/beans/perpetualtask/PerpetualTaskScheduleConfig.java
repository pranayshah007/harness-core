/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.perpetualtask;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PerpetualTaskScheduleConfigKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "perpetualTaskScheduleConfig", noClassnameStored = true)
@OwnedBy(HarnessTeam.PL)
public class PerpetualTaskScheduleConfig implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("unique_perpetualTaskScheduleConfig_index1")
                 .field(PerpetualTaskScheduleConfigKeys.accountId)
                 .field(PerpetualTaskScheduleConfigKeys.perpetualTaskType)
                 .build())
        .build();
  }
  @Id @NotNull private String uuid;
  private String accountId;
  private String perpetualTaskType;
  private long timeIntervalInMillis;
}
