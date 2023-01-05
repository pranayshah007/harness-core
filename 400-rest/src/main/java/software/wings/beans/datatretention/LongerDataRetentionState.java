/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.datatretention;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "LongerDataRetentionStateKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "longerDataRetentionState", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class LongerDataRetentionState implements PersistentEntity, CreatedAtAware, UpdatedAtAware {
  @Id private String uuid;
  public static final String INSTANCE_LONGER_RETENTION = "instanceLongerDataRetentionState";
  public static final String DEPLOYMENT_LONGER_RETENTION = "deploymentLongerDataRetentionState";

  Map<String, Boolean> keyRetentionCompletedMap;

  @FdUniqueIndex private String accountId;
  private long createdAt;
  private long lastUpdatedAt;
}
