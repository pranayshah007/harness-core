package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.cvng.core.beans.StackdriverDefinition;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("STACKDRIVER")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "StackdriverCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StackdriverCVConfig extends MetricCVConfig {
  private List<MetricInfo> metricInfoList;
  private String dashboardName;
  private String dashboardPath;

  @Data
  @SuperBuilder
  @FieldNameConstants(innerTypeName = "MetricInfoKeys")
  public static class MetricInfo extends AnalysisInfo {
    private String metricName;
    private String jsonMetricDefinition;
    private List<String> tags;
    private TimeSeriesMetricType metricType;
    boolean isManualQuery;
    private String serviceInstanceField;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.STACKDRIVER;
  }

  @Override
  @JsonIgnore
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  protected void validateParams() {
    checkNotNull(metricInfoList, generateErrorMessageFromParam(StackdriverCVConfigKeys.metricInfoList));

    metricInfoList.forEach(
        metricInfo -> { StackDriverMetricDefinition.extractFromJson(metricInfo.getJsonMetricDefinition()); });
  }

  public void fromStackdriverDefinitions(
      List<StackdriverDefinition> stackdriverDefinitions, CVMonitoringCategory category) {
    Preconditions.checkNotNull(stackdriverDefinitions);
    if (metricInfoList == null) {
      metricInfoList = new ArrayList<>();
    }
    dashboardName = stackdriverDefinitions.get(0).getDashboardName();
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.STACKDRIVER)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(category.getDisplayName())
                                .build();

    stackdriverDefinitions.forEach(definition -> {
      TimeSeriesMetricType metricType = definition.getRiskProfile().getMetricType();
      metricInfoList.add(
          MetricInfo.builder()
              .metricName(definition.getMetricName())
              .identifier(definition.getIdentifier())
              .jsonMetricDefinition(JsonUtils.asJson(definition.getJsonMetricDefinition()))
              .metricType(metricType)
              .tags(definition.getMetricTags())
              .isManualQuery(definition.isManualQuery())
              .serviceInstanceField(definition.getServiceInstanceField())
              .sli(SLIMetricTransformer.transformDTOtoEntity(definition.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(definition.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(definition.getAnalysis()))
              .build());

      // add this metric to the pack and the corresponding thresholds
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          definition.getMetricName(), metricType, definition.getRiskProfile().getThresholdTypes());
      metricPack.addToMetrics(MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(metricType)
                                  .name(definition.getMetricName())
                                  .included(true)
                                  .build());
    });
    this.setMetricPack(metricPack);
  }

  public static class StackDriverCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<StackdriverCVConfig, StackdriverCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<StackdriverCVConfig> updateOperations, StackdriverCVConfig stackdriverCVConfig) {
      setCommonOperations(updateOperations, stackdriverCVConfig);
      updateOperations.set(StackdriverCVConfigKeys.metricInfoList, stackdriverCVConfig.getMetricInfoList());
      updateOperations.set(StackdriverCVConfigKeys.dashboardName, stackdriverCVConfig.getDashboardName());
      updateOperations.set(StackdriverCVConfigKeys.dashboardPath, stackdriverCVConfig.getDashboardPath());
    }
  }
}
