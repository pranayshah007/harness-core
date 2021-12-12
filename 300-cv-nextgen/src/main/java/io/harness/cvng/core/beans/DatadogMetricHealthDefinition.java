package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class DatadogMetricHealthDefinition extends HealthSourceMetricDefinition {
  String dashboardId;
  String dashboardName;
  String query;
  String groupingQuery;
  String metric;
  String aggregation;
  String serviceInstanceIdentifierTag;
  List<String> metricTags;
  boolean isManualQuery;

  @JsonProperty(value = "isManualQuery")
  public boolean isManualQuery() {
    return isManualQuery;
  }

  public String getServiceInstanceIdentifierTag() {
    if (Objects.nonNull(analysis) && Objects.nonNull(analysis.getDeploymentVerification())
        && Objects.nonNull(analysis.deploymentVerification.serviceInstanceFieldName)) {
      return analysis.deploymentVerification.serviceInstanceFieldName;
    }
    return serviceInstanceIdentifierTag;
  }
}
