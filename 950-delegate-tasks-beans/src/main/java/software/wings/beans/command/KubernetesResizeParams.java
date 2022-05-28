/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.delegate.beans.pcf.ResizeStrategy;

import software.wings.api.ContainerServiceData;
import software.wings.beans.InstanceUnitType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesResizeParams extends ContainerResizeParams {
  private String namespace;
  private boolean useAutoscaler;
  private String autoscalerYaml;
  private String apiVersion;
  private String subscriptionId;
  private String resourceGroup;
  private boolean useIstioRouteRule;
  private Integer trafficPercent;
  private boolean useNewLabelMechanism;
  private Map<String, String> lookupLabels;
  // For supporting delegate capability framework in K8s
  private String masterUrl;

  public static final class KubernetesResizeParamsBuilder {
    private String clusterName;
    private int serviceSteadyStateTimeout;
    private String namespace;
    private boolean rollback;
    private boolean useAutoscaler;
    private boolean rollbackAllPhases;
    private String autoscalerYaml;
    private String containerServiceName;
    private String apiVersion;
    private String subscriptionId;
    private String image;
    private String resourceGroup;
    private ResizeStrategy resizeStrategy;
    private boolean useIstioRouteRule;
    private boolean useFixedInstances;
    private Integer trafficPercent;
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
    private boolean useNewLabelMechanism;
    private Map<String, String> lookupLabels;
    private String masterUrl;

    private KubernetesResizeParamsBuilder() {}

    public static KubernetesResizeParamsBuilder aKubernetesResizeParams() {
      return new KubernetesResizeParamsBuilder();
    }

    public KubernetesResizeParamsBuilder withClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
    }

    public KubernetesResizeParamsBuilder withServiceSteadyStateTimeout(int serviceSteadyStateTimeout) {
      this.serviceSteadyStateTimeout = serviceSteadyStateTimeout;
      return this;
    }

    public KubernetesResizeParamsBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public KubernetesResizeParamsBuilder withRollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public KubernetesResizeParamsBuilder withUseAutoscaler(boolean useAutoscaler) {
      this.useAutoscaler = useAutoscaler;
      return this;
    }

    public KubernetesResizeParamsBuilder withRollbackAllPhases(boolean rollbackAllPhases) {
      this.rollbackAllPhases = rollbackAllPhases;
      return this;
    }

    public KubernetesResizeParamsBuilder withAutoscalerYaml(String autoscalerYaml) {
      this.autoscalerYaml = autoscalerYaml;
      return this;
    }

    public KubernetesResizeParamsBuilder withContainerServiceName(String containerServiceName) {
      this.containerServiceName = containerServiceName;
      return this;
    }

    public KubernetesResizeParamsBuilder withApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
      return this;
    }

    public KubernetesResizeParamsBuilder withSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
      return this;
    }

    public KubernetesResizeParamsBuilder withImage(String image) {
      this.image = image;
      return this;
    }

    public KubernetesResizeParamsBuilder withResourceGroup(String resourceGroup) {
      this.resourceGroup = resourceGroup;
      return this;
    }

    public KubernetesResizeParamsBuilder withResizeStrategy(ResizeStrategy resizeStrategy) {
      this.resizeStrategy = resizeStrategy;
      return this;
    }

    public KubernetesResizeParamsBuilder withUseIstioRouteRule(boolean useIstioRouteRule) {
      this.useIstioRouteRule = useIstioRouteRule;
      return this;
    }

    public KubernetesResizeParamsBuilder withUseFixedInstances(boolean useFixedInstances) {
      this.useFixedInstances = useFixedInstances;
      return this;
    }

    public KubernetesResizeParamsBuilder withTrafficPercent(Integer trafficPercent) {
      this.trafficPercent = trafficPercent;
      return this;
    }

    public KubernetesResizeParamsBuilder withMaxInstances(int maxInstances) {
      this.maxInstances = maxInstances;
      return this;
    }

    public KubernetesResizeParamsBuilder withFixedInstances(int fixedInstances) {
      this.fixedInstances = fixedInstances;
      return this;
    }

    public KubernetesResizeParamsBuilder withNewInstanceData(List<ContainerServiceData> newInstanceData) {
      this.newInstanceData = newInstanceData;
      return this;
    }

    public KubernetesResizeParamsBuilder withOldInstanceData(List<ContainerServiceData> oldInstanceData) {
      this.oldInstanceData = oldInstanceData;
      return this;
    }

    public KubernetesResizeParamsBuilder withInstanceCount(Integer instanceCount) {
      this.instanceCount = instanceCount;
      return this;
    }

    public KubernetesResizeParamsBuilder withInstanceUnitType(InstanceUnitType instanceUnitType) {
      this.instanceUnitType = instanceUnitType;
      return this;
    }

    public KubernetesResizeParamsBuilder withDownsizeInstanceCount(Integer downsizeInstanceCount) {
      this.downsizeInstanceCount = downsizeInstanceCount;
      return this;
    }

    public KubernetesResizeParamsBuilder withDownsizeInstanceUnitType(InstanceUnitType downsizeInstanceUnitType) {
      this.downsizeInstanceUnitType = downsizeInstanceUnitType;
      return this;
    }

    public KubernetesResizeParamsBuilder withOriginalServiceCounts(List<String[]> originalServiceCounts) {
      this.originalServiceCounts = originalServiceCounts;
      return this;
    }

    public KubernetesResizeParamsBuilder withOriginalTrafficWeights(List<String[]> originalTrafficWeights) {
      this.originalTrafficWeights = originalTrafficWeights;
      return this;
    }

    public KubernetesResizeParamsBuilder withUseNewLabelMechanism(boolean useNewLabelMechanism) {
      this.useNewLabelMechanism = useNewLabelMechanism;
      return this;
    }

    public KubernetesResizeParamsBuilder withLookupLabels(Map<String, String> lookupLabels) {
      this.lookupLabels = lookupLabels;
      return this;
    }

    public KubernetesResizeParamsBuilder withMasterUrl(String masterUrl) {
      this.masterUrl = masterUrl;
      return this;
    }

    public KubernetesResizeParamsBuilder but() {
      return aKubernetesResizeParams()
          .withClusterName(clusterName)
          .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
          .withNamespace(namespace)
          .withRollback(rollback)
          .withUseAutoscaler(useAutoscaler)
          .withRollbackAllPhases(rollbackAllPhases)
          .withAutoscalerYaml(autoscalerYaml)
          .withContainerServiceName(containerServiceName)
          .withApiVersion(apiVersion)
          .withSubscriptionId(subscriptionId)
          .withImage(image)
          .withResourceGroup(resourceGroup)
          .withResizeStrategy(resizeStrategy)
          .withUseIstioRouteRule(useIstioRouteRule)
          .withUseFixedInstances(useFixedInstances)
          .withTrafficPercent(trafficPercent)
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
          .withUseNewLabelMechanism(useNewLabelMechanism)
          .withLookupLabels(lookupLabels)
          .withMasterUrl(masterUrl);
    }

    public KubernetesResizeParams build() {
      KubernetesResizeParams kubernetesResizeParams = new KubernetesResizeParams();
      kubernetesResizeParams.setClusterName(clusterName);
      kubernetesResizeParams.setServiceSteadyStateTimeout(serviceSteadyStateTimeout);
      kubernetesResizeParams.setNamespace(namespace);
      kubernetesResizeParams.setRollback(rollback);
      kubernetesResizeParams.setUseAutoscaler(useAutoscaler);
      kubernetesResizeParams.setRollbackAllPhases(rollbackAllPhases);
      kubernetesResizeParams.setAutoscalerYaml(autoscalerYaml);
      kubernetesResizeParams.setContainerServiceName(containerServiceName);
      kubernetesResizeParams.setApiVersion(apiVersion);
      kubernetesResizeParams.setSubscriptionId(subscriptionId);
      kubernetesResizeParams.setImage(image);
      kubernetesResizeParams.setResourceGroup(resourceGroup);
      kubernetesResizeParams.setResizeStrategy(resizeStrategy);
      kubernetesResizeParams.setUseIstioRouteRule(useIstioRouteRule);
      kubernetesResizeParams.setUseFixedInstances(useFixedInstances);
      kubernetesResizeParams.setTrafficPercent(trafficPercent);
      kubernetesResizeParams.setMaxInstances(maxInstances);
      kubernetesResizeParams.setFixedInstances(fixedInstances);
      kubernetesResizeParams.setNewInstanceData(newInstanceData);
      kubernetesResizeParams.setOldInstanceData(oldInstanceData);
      kubernetesResizeParams.setInstanceCount(instanceCount);
      kubernetesResizeParams.setInstanceUnitType(instanceUnitType);
      kubernetesResizeParams.setDownsizeInstanceCount(downsizeInstanceCount);
      kubernetesResizeParams.setDownsizeInstanceUnitType(downsizeInstanceUnitType);
      kubernetesResizeParams.setOriginalServiceCounts(originalServiceCounts);
      kubernetesResizeParams.setOriginalTrafficWeights(originalTrafficWeights);
      kubernetesResizeParams.setUseNewLabelMechanism(useNewLabelMechanism);
      kubernetesResizeParams.setLookupLabels(lookupLabels);
      kubernetesResizeParams.setMasterUrl(masterUrl);
      return kubernetesResizeParams;
    }
  }
}
