package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec.NewRelicMetricDefinition;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("NEW_RELIC")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "NewRelicCVConfigKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NewRelicCVConfig extends MetricCVConfig {
  private String applicationName;
  private long applicationId;
  private String groupName;
  private List<NewRelicMetricInfo> metricInfos;
  private HealthSourceQueryType queryType;

  @Override
  public DataSourceType getType() {
    return DataSourceType.NEW_RELIC;
  }

  @Override
  @JsonIgnore
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  protected void validateParams() {
    boolean appIdPresent = false, customMetricPresent = false;
    if (isEmpty(metricInfos)) {
      checkNotNull(applicationName, generateErrorMessageFromParam(NewRelicCVConfigKeys.applicationName));
      checkNotNull(applicationId, generateErrorMessageFromParam(NewRelicCVConfigKeys.applicationId));
      appIdPresent = true;
    } else {
      customMetricPresent = true;
    }
    Preconditions.checkState(appIdPresent || customMetricPresent,
        "CVConfig should have either application based setup or custom metric setup or both.");
  }

  public static class NewRelicCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<NewRelicCVConfig, NewRelicCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<NewRelicCVConfig> updateOperations, NewRelicCVConfig newRelicCVConfig) {
      setCommonOperations(updateOperations, newRelicCVConfig);
      updateOperations.set(NewRelicCVConfigKeys.applicationName, newRelicCVConfig.getApplicationName())
          .set(NewRelicCVConfigKeys.applicationId, newRelicCVConfig.getApplicationId());
    }
  }

  public void populateFromMetricDefinitions(
      List<NewRelicMetricDefinition> metricDefinitions, CVMonitoringCategory category) {
    if (this.metricInfos == null) {
      this.metricInfos = new ArrayList<>();
    }
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.NEW_RELIC)
                                .projectIdentifier(getProjectIdentifier())
                                .orgIdentifier(getOrgIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .category(category)
                                .build();

    metricDefinitions.stream().filter(md -> md.getGroupName().equals(getGroupName())).forEach(md -> {
      NewRelicMetricInfo info =
          NewRelicMetricInfo.builder()
              .identifier(md.getIdentifier())
              .metricName(md.getMetricName())
              .nrql(md.getNrql())
              .responseMapping(md.getResponseMapping())
              .sli(SLIMetricTransformer.transformDTOtoEntity(md.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(md.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(md.getAnalysis()))
              .metricType(md.getRiskProfile().getMetricType())
              .build();
      this.metricInfos.add(info);
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          info.getMetricName(), info.getMetricType(), md.getRiskProfile().getThresholdTypes());

      metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(info.getMetricType())
                                  .name(info.getMetricName())
                                  .included(true)
                                  .build());
    });

    this.setMetricPack(metricPack);
  }

  @Value
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class NewRelicMetricInfo extends AnalysisInfo {
    String metricName;
    String nrql;
    TimeSeriesMetricType metricType;
    MetricResponseMapping responseMapping;
  }
}
