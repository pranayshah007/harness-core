package io.harness.ccm.remote.beans.governance;

import static io.harness.filter.FilterConstants.EXECUTION_FILTER;

import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.views.helper.ExecutionStatus;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.filter.entity.FilterProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ExecutionFilterProperty")
@JsonTypeName(EXECUTION_FILTER)
public class ExecutionFilterProperty extends FilterProperties {
  List<String> policyId;
  List<String> ruleSet;
  List<String> region;
  ExecutionStatus executionStatus;
  RuleCloudProviderType cloudProvider;
  String targetAccount;

  List<CCMTimeFilter> timeFilters;
  Integer offset;
  Integer limit;
}
