/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.annotations.StoreIn;
import io.harness.beans.ExecutionStatus;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.version.ServiceApiVersion;

import software.wings.beans.Base;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

/**
 * Created by rsingh on 1/8/18.
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@FieldNameConstants(innerTypeName = "LearningEngineExperimentalAnalysisTaskKeys")
@IgnoreUnusedIndex
@StoreIn(DbAliases.HARNESS)
@Entity(value = "learningEngineExperimentalAnalysisTask", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class LearningEngineExperimentalAnalysisTask extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("taskQueueIdx")
                 .field(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id)
                 .descSortField(LearningEngineExperimentalAnalysisTaskKeys.analysis_minute)
                 .field(LearningEngineExperimentalAnalysisTaskKeys.executionStatus)
                 .field(LearningEngineExperimentalAnalysisTaskKeys.ml_analysis_type)
                 .field(LearningEngineExperimentalAnalysisTaskKeys.cluster_level)
                 .field(LearningEngineExperimentalAnalysisTaskKeys.group_name)
                 .field(LearningEngineExperimentalAnalysisTaskKeys.version)
                 .descSortField(CREATED_AT_KEY)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("cvConfigStatusIdx")
                .field(LearningEngineExperimentalAnalysisTaskKeys.cvConfigId)
                .descSortField(LearningEngineExperimentalAnalysisTaskKeys.analysis_minute)
                .field(LearningEngineExperimentalAnalysisTaskKeys.executionStatus)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("usageMetricsIndex")
                .field(LearningEngineExperimentalAnalysisTaskKeys.executionStatus)
                .field(LearningEngineExperimentalAnalysisTaskKeys.ml_analysis_type)
                .field(LearningEngineExperimentalAnalysisTaskKeys.is24x7Task)
                .descSortField(CREATED_AT_KEY)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("taskFetchIdx")
                .field(LearningEngineExperimentalAnalysisTaskKeys.experiment_name)
                .field(LearningEngineExperimentalAnalysisTaskKeys.executionStatus)
                .field(LearningEngineExperimentalAnalysisTaskKeys.retry)
                .descSortField(CREATED_AT_KEY)
                .build())
        .build();
  }
  public static long TIME_SERIES_ANALYSIS_TASK_TIME_OUT = TimeUnit.MINUTES.toMillis(2);
  public static final int RETRIES = 3;
  @FdIndex private String accountId;
  private String workflow_id;
  private String workflow_execution_id;
  private String state_execution_id;
  private String service_id;
  private String auth_token;
  private int analysis_start_min;
  private long analysis_minute;
  private int smooth_window;
  private int tolerance;
  private int min_rpm;
  private int comparison_unit_window;
  private int parallel_processes;
  private String test_input_url;
  private String control_input_url;
  @Default private String group_name = NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
  private TimeSeriesMlAnalysisType time_series_ml_analysis_type;
  private String analysis_save_url;
  private String metric_template_url;
  private String log_analysis_get_url;
  private int analysis_start_time;
  private double sim_threshold;
  private Integer cluster_level;
  private List<String> query;
  private Set<String> control_nodes;
  private Set<String> test_nodes;
  private StateType stateType;
  private MLAnalysisType ml_analysis_type;
  private String experiment_name;
  private String feedback_url;
  private String feature_name;
  private String cvConfigId;
  private int prediction_start_time;
  private String previous_analysis_url;
  private String historical_analysis_url;
  private String previous_anomalies_url;
  private String cumulative_sums_url;
  private boolean is24x7Task;
  private String tag;
  private AnalysisComparisonStrategy analysis_comparison_strategy;
  private ExecutionStatus executionStatus;
  private Double alertThreshold;
  @JsonProperty("log_ml_result_url") private String logMLResultUrl;
  @JsonProperty("use_supervised_model") private boolean shouldUseSupervisedModel;
  @JsonProperty("key_transactions_url") private String keyTransactionsUrl;

  @Builder.Default
  private ServiceApiVersion version = ServiceApiVersion.values()[ServiceApiVersion.values().length - 1];

  private int retry;

  @Default
  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusHours(8).toInstant());
}
