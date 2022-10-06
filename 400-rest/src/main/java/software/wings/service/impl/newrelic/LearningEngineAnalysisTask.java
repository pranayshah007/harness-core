/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
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
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by rsingh on 1/8/18.
 */
@Data
@Builder
@FieldNameConstants(innerTypeName = "LearningEngineAnalysisTaskKeys")
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "learningEngineAnalysisTask", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(CV)
@TargetModule(HarnessModule._270_VERIFICATION)
public class LearningEngineAnalysisTask extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("task_fetch_idx")
                 .field(LearningEngineAnalysisTaskKeys.state_execution_id)
                 .field(LearningEngineAnalysisTaskKeys.analysis_minute)
                 .field(LearningEngineAnalysisTaskKeys.executionStatus)
                 .field(LearningEngineAnalysisTaskKeys.ml_analysis_type)
                 .field(LearningEngineAnalysisTaskKeys.cluster_level)
                 .field(LearningEngineAnalysisTaskKeys.group_name)
                 .field(LearningEngineAnalysisTaskKeys.version)
                 .descSortField(CREATED_AT_KEY)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("taskFetchPriorityIdx")
                .field(LearningEngineAnalysisTaskKeys.state_execution_id)
                .field(LearningEngineAnalysisTaskKeys.priority)
                .field(LearningEngineAnalysisTaskKeys.executionStatus)
                .field(LearningEngineAnalysisTaskKeys.ml_analysis_type)
                .field(LearningEngineAnalysisTaskKeys.cluster_level)
                .field(LearningEngineAnalysisTaskKeys.group_name)
                .field(LearningEngineAnalysisTaskKeys.version)
                .descSortField(CREATED_AT_KEY)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("cv_config_status_index")
                .field(LearningEngineAnalysisTaskKeys.cvConfigId)
                .descSortField(LearningEngineAnalysisTaskKeys.analysis_minute)
                .field(LearningEngineAnalysisTaskKeys.executionStatus)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("usageMetricsIndex")
                .field(LearningEngineAnalysisTaskKeys.executionStatus)
                .field(LearningEngineAnalysisTaskKeys.ml_analysis_type)
                .field(LearningEngineAnalysisTaskKeys.is24x7Task)
                .descSortField(CREATED_AT_KEY)
                .build())
        .build();
  }
  public static long TIME_SERIES_ANALYSIS_TASK_TIME_OUT = TimeUnit.MINUTES.toMillis(12);
  public static final int RETRIES = 3;

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
  private String analysis_save_url;
  private String metric_template_url;
  private String log_analysis_get_url;
  private String previous_analysis_url;
  private String historical_analysis_url;
  private String previous_anomalies_url;
  private String cumulative_sums_url;
  private String previous_test_analysis_url;
  private String analysis_failure_url;
  @JsonProperty("key_transactions_url") private String keyTransactionsUrl;
  @Default private String group_name = NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
  private TimeSeriesMlAnalysisType time_series_ml_analysis_type;
  private AnalysisComparisonStrategy analysis_comparison_strategy;
  private int analysis_start_time;
  private int prediction_start_time;
  private double sim_threshold;
  private Integer cluster_level;
  private List<String> query;
  private Set<String> control_nodes;
  private Set<String> test_nodes;
  private StateType stateType;
  private MLAnalysisType ml_analysis_type;
  private String feedback_url;
  @JsonProperty("log_ml_result_url") private String logMLResultUrl;
  @JsonProperty("use_supervised_model") private boolean shouldUseSupervisedModel;
  private String feature_name;
  @FdIndex private ExecutionStatus executionStatus;
  private String cvConfigId;
  private boolean is24x7Task;
  private String tag = "default";
  private int service_guard_backoff_count;
  private Double alertThreshold;
  @FdIndex private String accountId;
  @JsonProperty("new_node_traffic_split_percentage") private Integer newInstanceTrafficSplitPercentage;

  @Builder.Default private int priority = 1;

  @Builder.Default
  private ServiceApiVersion version = ServiceApiVersion.values()[ServiceApiVersion.values().length - 1];

  private int retry;
  private long timeTaken;

  @Default
  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(7).toInstant());
}
