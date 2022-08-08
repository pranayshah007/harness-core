/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.pcf.ResizeStrategy;

import software.wings.api.ContainerServiceData;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.container.AwsAutoScalarConfig;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class EcsResizeParams extends ContainerResizeParams {
  private String region;
  private List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs;
  private List<AwsAutoScalarConfig> awsAutoScalarConfigForNewService;
  private boolean previousEcsAutoScalarsAlreadyRemoved;
  private boolean isLastDeployPhase;
  private boolean ecsAutoscalarRedesignEnabled;

  public static final class EcsResizeParamsBuilder {
    private String region;
    private String clusterName;
    private int serviceSteadyStateTimeout;
    private boolean rollback;
    private boolean rollbackAllPhases;
    private String containerServiceName;
    private String image;
    private ResizeStrategy resizeStrategy;
    private boolean useFixedInstances;
    private int maxInstances;
    private int fixedInstances;
    private List<ContainerServiceData> newInstanceData;
    private List<ContainerServiceData> oldInstanceData;
    private Integer instanceCount;
    private InstanceUnitType instanceUnitType;
    private Integer downsizeInstanceCount;
    private InstanceUnitType downsizeInstanceUnitType;
    private List<String[]> originalServiceCounts;
    private List<String[]> originalTrafficWeights;
    private List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs;
    private List<AwsAutoScalarConfig> awsAutoScalarConfigForNewService;
    private boolean previousEcsAutoScalarsAlreadyRemoved;
    private boolean isLastDeployPhase;
    private boolean ecsAutoscalarRedesignEnabled;

    private EcsResizeParamsBuilder() {}

    public static EcsResizeParamsBuilder anEcsResizeParams() {
      return new EcsResizeParamsBuilder();
    }

    public EcsResizeParamsBuilder withIsLastDeployPhase(boolean isLastDeployPhase) {
      this.isLastDeployPhase = isLastDeployPhase;
      return this;
    }

    public EcsResizeParamsBuilder withEcsAutoscalarRedesignEnabled(boolean ecsAutoscalarRedesignEnabled) {
      this.ecsAutoscalarRedesignEnabled = ecsAutoscalarRedesignEnabled;
      return this;
    }

    public EcsResizeParamsBuilder withRegion(String region) {
      this.region = region;
      return this;
    }

    public EcsResizeParamsBuilder withClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
    }

    public EcsResizeParamsBuilder withServiceSteadyStateTimeout(int serviceSteadyStateTimeout) {
      this.serviceSteadyStateTimeout = serviceSteadyStateTimeout;
      return this;
    }

    public EcsResizeParamsBuilder withRollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public EcsResizeParamsBuilder withRollbackAllPhases(boolean rollbackAllPhases) {
      this.rollbackAllPhases = rollbackAllPhases;
      return this;
    }

    public EcsResizeParamsBuilder withContainerServiceName(String containerServiceName) {
      this.containerServiceName = containerServiceName;
      return this;
    }

    public EcsResizeParamsBuilder withImage(String image) {
      this.image = image;
      return this;
    }

    public EcsResizeParamsBuilder withResizeStrategy(ResizeStrategy resizeStrategy) {
      this.resizeStrategy = resizeStrategy;
      return this;
    }

    public EcsResizeParamsBuilder withUseFixedInstances(boolean useFixedInstances) {
      this.useFixedInstances = useFixedInstances;
      return this;
    }

    public EcsResizeParamsBuilder withMaxInstances(int maxInstances) {
      this.maxInstances = maxInstances;
      return this;
    }

    public EcsResizeParamsBuilder withFixedInstances(int fixedInstances) {
      this.fixedInstances = fixedInstances;
      return this;
    }

    public EcsResizeParamsBuilder withNewInstanceData(List<ContainerServiceData> newInstanceData) {
      this.newInstanceData = newInstanceData;
      return this;
    }

    public EcsResizeParamsBuilder withOldInstanceData(List<ContainerServiceData> oldInstanceData) {
      this.oldInstanceData = oldInstanceData;
      return this;
    }

    public EcsResizeParamsBuilder withInstanceCount(Integer instanceCount) {
      this.instanceCount = instanceCount;
      return this;
    }

    public EcsResizeParamsBuilder withInstanceUnitType(InstanceUnitType instanceUnitType) {
      this.instanceUnitType = instanceUnitType;
      return this;
    }

    public EcsResizeParamsBuilder withDownsizeInstanceCount(Integer downsizeInstanceCount) {
      this.downsizeInstanceCount = downsizeInstanceCount;
      return this;
    }

    public EcsResizeParamsBuilder withDownsizeInstanceUnitType(InstanceUnitType downsizeInstanceUnitType) {
      this.downsizeInstanceUnitType = downsizeInstanceUnitType;
      return this;
    }

    public EcsResizeParamsBuilder withOriginalServiceCounts(List<String[]> originalServiceCounts) {
      this.originalServiceCounts = originalServiceCounts;
      return this;
    }

    public EcsResizeParamsBuilder withOriginalTrafficWeights(List<String[]> originalTrafficWeights) {
      this.originalTrafficWeights = originalTrafficWeights;
      return this;
    }

    public EcsResizeParamsBuilder withPreviousAwsAutoScalarConfigs(
        List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs) {
      this.previousAwsAutoScalarConfigs = previousAwsAutoScalarConfigs;
      return this;
    }

    public EcsResizeParamsBuilder withAwsAutoScalarConfigForNewService(
        List<AwsAutoScalarConfig> awsAutoScalarConfigForNewService) {
      this.awsAutoScalarConfigForNewService = awsAutoScalarConfigForNewService;
      return this;
    }

    public EcsResizeParamsBuilder withPreviousEcsAutoScalarsAlreadyRemoved(
        boolean previousEcsAutoScalarsAlreadyRemoved) {
      this.previousEcsAutoScalarsAlreadyRemoved = previousEcsAutoScalarsAlreadyRemoved;
      return this;
    }

    public EcsResizeParamsBuilder but() {
      return anEcsResizeParams()
          .withRegion(region)
          .withClusterName(clusterName)
          .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
          .withRollback(rollback)
          .withRollbackAllPhases(rollbackAllPhases)
          .withContainerServiceName(containerServiceName)
          .withImage(image)
          .withResizeStrategy(resizeStrategy)
          .withUseFixedInstances(useFixedInstances)
          .withMaxInstances(maxInstances)
          .withFixedInstances(fixedInstances)
          .withNewInstanceData(newInstanceData)
          .withOldInstanceData(oldInstanceData)
          .withInstanceCount(instanceCount)
          .withInstanceUnitType(instanceUnitType)
          .withDownsizeInstanceCount(downsizeInstanceCount)
          .withDownsizeInstanceUnitType(downsizeInstanceUnitType)
          .withOriginalServiceCounts(originalServiceCounts)
          .withOriginalTrafficWeights(originalTrafficWeights)
          .withPreviousAwsAutoScalarConfigs(previousAwsAutoScalarConfigs)
          .withAwsAutoScalarConfigForNewService(awsAutoScalarConfigForNewService)
          .withPreviousEcsAutoScalarsAlreadyRemoved(previousEcsAutoScalarsAlreadyRemoved)
          .withIsLastDeployPhase(isLastDeployPhase)
          .withEcsAutoscalarRedesignEnabled(ecsAutoscalarRedesignEnabled);
    }

    public EcsResizeParams build() {
      EcsResizeParams ecsResizeParams = new EcsResizeParams();
      ecsResizeParams.setRegion(region);
      ecsResizeParams.setClusterName(clusterName);
      ecsResizeParams.setServiceSteadyStateTimeout(serviceSteadyStateTimeout);
      ecsResizeParams.setRollback(rollback);
      ecsResizeParams.setRollbackAllPhases(rollbackAllPhases);
      ecsResizeParams.setContainerServiceName(containerServiceName);
      ecsResizeParams.setImage(image);
      ecsResizeParams.setResizeStrategy(resizeStrategy);
      ecsResizeParams.setUseFixedInstances(useFixedInstances);
      ecsResizeParams.setMaxInstances(maxInstances);
      ecsResizeParams.setFixedInstances(fixedInstances);
      ecsResizeParams.setNewInstanceData(newInstanceData);
      ecsResizeParams.setOldInstanceData(oldInstanceData);
      ecsResizeParams.setInstanceCount(instanceCount);
      ecsResizeParams.setInstanceUnitType(instanceUnitType);
      ecsResizeParams.setDownsizeInstanceCount(downsizeInstanceCount);
      ecsResizeParams.setDownsizeInstanceUnitType(downsizeInstanceUnitType);
      ecsResizeParams.setOriginalServiceCounts(originalServiceCounts);
      ecsResizeParams.setOriginalTrafficWeights(originalTrafficWeights);
      ecsResizeParams.setPreviousAwsAutoScalarConfigs(previousAwsAutoScalarConfigs);
      ecsResizeParams.setAwsAutoScalarConfigForNewService(awsAutoScalarConfigForNewService);
      ecsResizeParams.setPreviousEcsAutoScalarsAlreadyRemoved(previousEcsAutoScalarsAlreadyRemoved);
      ecsResizeParams.setLastDeployPhase(isLastDeployPhase);
      ecsResizeParams.setEcsAutoscalarRedesignEnabled(ecsAutoscalarRedesignEnabled);
      return ecsResizeParams;
    }
  }
}
