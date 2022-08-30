/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.metrics.TimeSeriesMetricDefinition;
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
@FieldNameConstants(innerTypeName = "TimeSeriesMetricTemplatesKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeSeriesMetricTemplates", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class TimeSeriesMetricTemplates extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_state_execution_per_config")
                 .unique(true)
                 .field(TimeSeriesMetricTemplatesKeys.stateExecutionId)
                 .field(TimeSeriesMetricTemplatesKeys.cvConfigId)
                 .build())
        .build();
  }

  @NotEmpty private StateType stateType;

  @NotEmpty private String stateExecutionId;

  @FdIndex private String cvConfigId;

  @FdIndex private String accountId;

  @NotEmpty private Map<String, TimeSeriesMetricDefinition> metricTemplates;

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;

  @Builder
  public TimeSeriesMetricTemplates(StateType stateType, String stateExecutionId, String cvConfigId, String accountId,
      Map<String, TimeSeriesMetricDefinition> metricTemplates) {
    this.stateType = stateType;
    this.stateExecutionId = stateExecutionId;
    this.cvConfigId = cvConfigId;
    this.accountId = accountId;
    this.metricTemplates = metricTemplates;
    this.validUntil = isEmpty(stateExecutionId) ? null : Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
  }
}
