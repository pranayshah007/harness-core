/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;

import io.harness.ng.DbAliases;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

/**
 * Class representing entity for TimeSeries Analysis Record.
 * Created by sriram_parthasarathy on 9/22/17.
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeSeriesAnalysisRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class TimeSeriesMLAnalysisRecord extends MetricAnalysisRecord {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("stateExIdx")
                 .field(MetricAnalysisRecordKeys.stateExecutionId)
                 .field(MetricAnalysisRecordKeys.groupName)
                 .descSortField(MetricAnalysisRecordKeys.analysisMinute)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("service_guard_idx")
                .field(MetricAnalysisRecordKeys.cvConfigId)
                .descSortField(MetricAnalysisRecordKeys.analysisMinute)
                .build(),
            CompoundMongoIndex.builder()
                .name("workflow_exec_appId_index")
                .field(MetricAnalysisRecordKeys.workflowExecutionId)
                .field("appId")
                .build())
        .build();
  }
}
