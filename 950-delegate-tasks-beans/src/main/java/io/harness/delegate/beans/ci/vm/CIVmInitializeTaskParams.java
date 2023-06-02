/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIVmInitializeTaskParams
    extends ConnectorTaskParams implements CIInitializeTaskParams, ExecutionCapabilityDemander {
  @NotNull private String poolID;
  @NotNull private String workingDir;

  @NotNull private String logKey;
  @NotNull private String logStreamUrl;
  @NotNull private String logSvcToken;
  @NotNull private boolean logSvcIndirectUpload;

  @NotNull private String tiUrl;
  @NotNull private String tiSvcToken;

  @NotNull private String stoUrl;
  @NotNull private String stoSvcToken;

  @NotNull private String accountID;
  @NotNull private String orgID;
  @NotNull private String projectID;
  @NotNull private String pipelineID;
  @NotNull private String stageID;
  @NotNull private String buildID;

  private Map<String, String> tags;

  @Expression(ALLOW_SECRETS) Map<String, String> environment;
  @Expression(ALLOW_SECRETS) private List<String> secrets;
  private ConnectorDetails gitConnector;
  private Map<String, String> volToMountPath;

  private String stageRuntimeId;
  @Expression(ALLOW_SECRETS) private List<VmServiceDependency> serviceDependencies;
  @Builder.Default private static final Type type = Type.VM;
  //@Getter private InfraInfo infraInfo;
  @Getter private CIInitializeTaskParams.Type infraInfo;

  private List<String> fallbackPoolIDs;

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(CIVmConnectionCapability.builder().poolId(poolID).infraInfo(infraInfo).build());
  }
}
