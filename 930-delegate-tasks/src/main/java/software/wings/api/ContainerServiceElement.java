/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.context.ContextElementType.CONTAINER_SERVICE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.ResizeStrategy;

import software.wings.api.ecs.EcsBGSetupData;
import software.wings.beans.AwsElbConfig;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.container.Label;
import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Created by rishi on 4/11/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@JsonTypeName("containerServiceElement")
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ContainerServiceElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String image;
  private boolean useFixedInstances;
  private int fixedInstances;
  private int maxInstances;
  private int serviceSteadyStateTimeout;
  private ResizeStrategy resizeStrategy;
  private String clusterName;
  private String namespace;
  private DeploymentType deploymentType;
  private String infraMappingId;
  private boolean useAutoscaler;
  private String autoscalerYaml;
  private int minAutoscaleInstances;
  private int maxAutoscaleInstances;
  private int targetCpuUtilizationPercentage;
  private String customMetricYamlConfig;
  private boolean useIstioRouteRule;
  private List<String[]> activeServiceCounts;
  private List<String[]> trafficWeights;
  private String controllerNamePrefix;
  private List<Label> lookupLabels;
  private List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs;
  private List<AwsAutoScalarConfig> newServiceAutoScalarConfig;
  private String newEcsServiceName;
  private boolean prevAutoscalarsAlreadyRemoved;
  private String loadBalancer;
  private String targetGroupForNewService;
  private String targetGroupForExistingService;
  private String ecsRegion;
  private List<AwsElbConfig> awsElbConfigs;
  private boolean isMultipleLoadBalancersFeatureFlagActive;

  // Only to be used by ECS BG
  private EcsBGSetupData ecsBGSetupData;

  @Override
  public ContextElementType getElementType() {
    return CONTAINER_SERVICE;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }

  @Override
  public String getType() {
    return "containerServiceElement";
  }
}
