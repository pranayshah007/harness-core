/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeSeriesMetricGroup", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "TimeSeriesMetricGroupKeys")
public class TimeSeriesMetricGroup extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_state_execution_per_type")
                 .unique(true)
                 .field(TimeSeriesMetricGroupKeys.stateType)
                 .field(TimeSeriesMetricGroupKeys.stateExecutionId)
                 .build())
        .build();
  }

  @NotEmpty private StateType stateType;

  @NotEmpty @FdIndex private String stateExecutionId;

  @NotEmpty private Map<String, TimeSeriesMlAnalysisGroupInfo> groups;

  @FdIndex private String accountId;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusWeeks(1).toInstant());

  @Builder
  public TimeSeriesMetricGroup(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, StateType stateType,
      String stateExecutionId, Map<String, TimeSeriesMlAnalysisGroupInfo> groups, String accountId) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.stateType = stateType;
    this.stateExecutionId = stateExecutionId;
    this.groups = groups;
    this.accountId = accountId;
  }

  @Data
  @Builder
  @EqualsAndHashCode(exclude = {"dependencyPath", "mlAnalysisType"})
  public static class TimeSeriesMlAnalysisGroupInfo {
    private String groupName;
    private String dependencyPath;
    private TimeSeriesMlAnalysisType mlAnalysisType;
  }
}
