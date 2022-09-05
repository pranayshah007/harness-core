/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.events.segment;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.data.structure.CollectionUtils;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.UpdatedAtAccess;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Value
@StoreIn(DbAliases.HARNESS)
@Entity(value = "segmentGroupEventJobContexts")
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "SegmentGroupEventJobContextKeys")
public class SegmentGroupEventJobContext implements PersistentRegularIterable, CreatedAtAccess, UpdatedAtAccess {
  @Id private String uuid;

  private List<String> accountIds;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long createdAt;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long lastUpdatedAt;

  public SegmentGroupEventJobContext(Long nextIteration, List<String> accountIds) {
    this.uuid = null;
    this.accountIds = CollectionUtils.emptyIfNull(accountIds);
    this.createdAt = System.currentTimeMillis();
    this.lastUpdatedAt = System.currentTimeMillis();
    this.nextIteration = nextIteration;
  }

  @FdIndex @NonFinal private Long nextIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  @Override
  public long getCreatedAt() {
    return 0;
  }

  @Override
  public long getLastUpdatedAt() {
    return 0;
  }
}
