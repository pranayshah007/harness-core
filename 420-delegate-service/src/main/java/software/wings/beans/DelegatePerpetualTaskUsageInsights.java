/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@FieldNameConstants(innerTypeName = "DelegatePerpetualTaskUsageInsightsKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegatePerpetualTaskUsageInsights", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DelegatePerpetualTaskUsageInsights implements PersistentEntity, UuidAware {
  @Id private String uuid;
  @FdIndex private long timestamp;
  private String accountId;
  private String taskId;
  private DelegatePerpetualTaskUsageInsightsEventType eventType;
  private String delegateId;
  private String delegateGroupId;
}
