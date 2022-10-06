/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification;

import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;
import static software.wings.common.VerificationConstants.MAX_NUM_ALERT_OCCURRENCES;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.beans.FeatureName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.NameAccess;

import software.wings.beans.Base;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

@Data
@FieldNameConstants(innerTypeName = "CVConfigurationKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "verificationServiceConfigurations")
@HarnessEntity(exportable = true)
public class CVConfiguration extends Base implements NameAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_name")
                 .unique(true)
                 .field("appId")
                 .field(CVConfigurationKeys.envId)
                 .field(CVConfigurationKeys.name)
                 .build())
        .build();
  }

  @NotNull private String name;
  @NotNull @FdIndex private String accountId;
  @NotNull private String connectorId;
  @NotNull @FdIndex private String envId;
  @NotNull @FdIndex private String serviceId;
  @NotNull private StateType stateType;
  @NotNull private AnalysisTolerance analysisTolerance;
  private String customThresholdRefId;
  private boolean enabled24x7;
  private AnalysisComparisonStrategy comparisonStrategy;
  private String contextId;
  private boolean isWorkflowConfig;
  private boolean alertEnabled;
  private double alertThreshold = 0.5;
  private int numOfOccurrencesForAlert = 1;
  private long snoozeStartTime;
  private long snoozeEndTime;

  @Transient @SchemaIgnore private String connectorName;
  @Transient @SchemaIgnore private String serviceName;
  @Transient @SchemaIgnore private String envName;
  @Transient @SchemaIgnore private String appName;

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;

  public AnalysisComparisonStrategy getComparisonStrategy() {
    return comparisonStrategy == null ? AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS : comparisonStrategy;
  }

  public void setAlertThreshold(double threshold) {
    // Validations for yaml
    Preconditions.checkArgument(
        threshold >= 0.0 && threshold <= 1.0, "Provided alert threshold is not between 0 and 1");
    this.alertThreshold = threshold;
  }

  public void setNumOfOccurrencesForAlert(int occurrences) {
    Preconditions.checkArgument(occurrences > 0 && occurrences <= MAX_NUM_ALERT_OCCURRENCES,
        "Provided number of occurrences for alert is not between 0 and " + MAX_NUM_ALERT_OCCURRENCES);
    this.numOfOccurrencesForAlert = occurrences;
  }

  // This should be an abstract method, but currently class cannot be converted to abstract due to multiple
  // instantiations of this class
  @JsonIgnore
  public CVConfiguration deepCopy() {
    throw new UnsupportedOperationException("Deep clone is being called from base method");
  }

  @JsonIgnore
  public DataCollectionInfoV2 toDataCollectionInfo() {
    throw new UnsupportedOperationException("DataCollectionInfo creation is not supported for this type.");
  }

  @JsonIgnore
  public boolean isCVTaskBasedCollectionEnabled() {
    return false;
  }
  @JsonIgnore
  public boolean isCVTaskBasedCollectionFeatureFlagged() {
    return false;
  }
  @JsonIgnore
  public FeatureName getCVTaskBasedCollectionFeatureFlag() {
    throw new UnsupportedOperationException(
        "This needs to be implemented if isCVTaskBasedCollectionEnabled returns true");
  }

  protected void fillDataCollectionInfoWithCommonFields(DataCollectionInfoV2 dataCollectionInfo) {
    dataCollectionInfo.setConnectorId(this.getConnectorId());
    dataCollectionInfo.setCvConfigId(this.getUuid());
    dataCollectionInfo.setAccountId(this.getAccountId());
    dataCollectionInfo.setApplicationId(this.getAppId());
    dataCollectionInfo.setServiceId(this.getServiceId());
    dataCollectionInfo.setEnvId(this.getEnvId());
    dataCollectionInfo.setStateExecutionId(CV_24x7_STATE_EXECUTION + "-" + this.getUuid());
    dataCollectionInfo.setHosts(Collections.emptySet());
  }

  protected void copy(CVConfiguration cvConfiguration) {
    cvConfiguration.setName(this.getName());
    cvConfiguration.setAccountId(this.getAccountId());
    cvConfiguration.setConnectorId(this.getConnectorId());
    cvConfiguration.setEnvId(this.getEnvId());
    cvConfiguration.setServiceId(this.getServiceId());
    cvConfiguration.setStateType(this.getStateType());
    cvConfiguration.setAnalysisTolerance(this.getAnalysisTolerance());
    cvConfiguration.setEnabled24x7(this.isEnabled24x7());
    cvConfiguration.setComparisonStrategy(this.getComparisonStrategy());
    cvConfiguration.setContextId(this.getContextId());
    cvConfiguration.setWorkflowConfig(this.isWorkflowConfig());
    cvConfiguration.setAlertEnabled(this.isAlertEnabled());
    cvConfiguration.setAlertThreshold(this.getAlertThreshold());
    cvConfiguration.setSnoozeStartTime(this.getSnoozeStartTime());
    cvConfiguration.setSnoozeEndTime(this.getSnoozeEndTime());
    cvConfiguration.setAppId(this.getAppId());
  }

  @JsonIgnore
  public List<ServiceGuardThroughputToErrorsMap> getThroughputToErrors() {
    return Collections.emptyList();
  }
}
