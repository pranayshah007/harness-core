/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ResourceConstraintInstanceKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "resourceConstraintInstances", noClassnameStored = true)
@HarnessEntity(exportable = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ResourceConstraintInstance implements PersistentRegularIterable, UuidAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueUnitOrder")
                 .unique(true)
                 .field(ResourceConstraintInstanceKeys.resourceConstraintId)
                 .field(ResourceConstraintInstanceKeys.resourceUnit)
                 .field(ResourceConstraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("usageIndex")
                 .field(ResourceConstraintInstanceKeys.resourceConstraintId)
                 .field(ResourceConstraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("iterationIndex")
                 .field(ResourceConstraintInstanceKeys.state)
                 .field(ResourceConstraintInstanceKeys.nextIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("app_release_entity")
                 .field(ResourceConstraintInstanceKeys.appId)
                 .field(ResourceConstraintInstanceKeys.releaseEntityType)
                 .field(ResourceConstraintInstanceKeys.releaseEntityId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("constraintStateUnitOrderIdx")
                 .field(ResourceConstraintInstanceKeys.resourceConstraintId)
                 .field(ResourceConstraintInstanceKeys.state)
                 .field(ResourceConstraintInstanceKeys.resourceUnit)
                 .field(ResourceConstraintInstanceKeys.order)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("releaseEntity_state")
                 .field(ResourceConstraintInstanceKeys.releaseEntityId)
                 .field(ResourceConstraintInstanceKeys.state)
                 .build())

        .build();
  }
  public static final List<String> NOT_FINISHED_STATES =
      ImmutableList.<String>builder().add(State.ACTIVE.name()).add(State.BLOCKED.name()).build();

  @Id @NotNull(groups = {Update.class}) private String uuid;
  @NotNull protected String appId;

  @FdIndex private String accountId;

  private String resourceConstraintId;
  private String resourceUnit;
  private int order;

  private String state;
  private int permits;

  private String releaseEntityType;
  private String releaseEntityId;

  private long acquiredAt;

  private Long nextIteration;

  @Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}
