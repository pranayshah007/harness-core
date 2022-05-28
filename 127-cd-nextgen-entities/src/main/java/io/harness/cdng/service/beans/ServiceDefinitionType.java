/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import io.harness.beans.ExecutionStrategyType;
import io.harness.ng.core.k8s.ServiceSpecType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fabric8.utils.Lists;
import java.util.Arrays;
import java.util.List;

public enum ServiceDefinitionType {
  @JsonProperty(ServiceSpecType.KUBERNETES)
  KUBERNETES(ServiceSpecType.KUBERNETES,
      Lists.newArrayList(ExecutionStrategyType.ROLLING, ExecutionStrategyType.BLUE_GREEN, ExecutionStrategyType.CANARY,
          ExecutionStrategyType.DEFAULT),
      ServiceSpecType.KUBERNETES),

  @JsonProperty(ServiceSpecType.NATIVE_HELM)
  NATIVE_HELM(ServiceSpecType.NATIVE_HELM,
      Lists.newArrayList(ExecutionStrategyType.ROLLING, ExecutionStrategyType.DEFAULT), ServiceSpecType.NATIVE_HELM),

  @JsonProperty(ServiceSpecType.SSH)
  SSH(ServiceSpecType.SSH, Lists.newArrayList(ExecutionStrategyType.DEFAULT), ServiceSpecType.SSH),

  @JsonProperty(ServiceSpecType.WINRM)
  WINRM(ServiceSpecType.WINRM, Lists.newArrayList(ExecutionStrategyType.DEFAULT), ServiceSpecType.WINRM),

  @JsonProperty(ServiceSpecType.SERVERLESS_AWS_LAMBDA)
  SERVERLESS_AWS_LAMBDA("Serverless Aws Lambda",
      Lists.newArrayList(ExecutionStrategyType.BASIC, ExecutionStrategyType.DEFAULT),
      ServiceSpecType.SERVERLESS_AWS_LAMBDA);

  /*
  //Unsupported for now
  //Also commented out in
  125-cd-nextgen/src/test/java/io/harness/cdng/pipeline/resources/CDNGPipelineConfigurationResourceTest.java
  //Also add test in "CDNGPipelineConfigurationHelperTest"
  @JsonProperty(ServiceSpecType.NATIVE_HELM)
  NATIVE_HELM("NativeHelm", Lists.newArrayList(ExecutionStrategyType.BASIC), ServiceSpecType.NATIVE_HELM);
  @JsonProperty(ServiceSpecType.ECS)
  ECS("Ecs",
    Lists.newArrayList(ExecutionStrategyType.BASIC, ExecutionStrategyType.BLUE_GREEN, ExecutionStrategyType.CANARY),
    ServiceSpecType.ECS),
  @JsonProperty(ServiceSpecType.SSH) SSH("Ssh", Lists.newArrayList(ExecutionStrategyType.BASIC), ServiceSpecType.SSH),
  @JsonProperty(ServiceSpecType.PCF)
  PCF("Pcf",
      Lists.newArrayList(ExecutionStrategyType.BASIC, ExecutionStrategyType.BLUE_GREEN, ExecutionStrategyType.CANARY),
      ServiceSpecType.PCF);
  */

  private String displayName;
  private String yamlName;
  private List<ExecutionStrategyType> executionStrategies;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ServiceDefinitionType getServiceDefinitionType(@JsonProperty("type") String yamlName) {
    for (ServiceDefinitionType serviceDefinitionType : ServiceDefinitionType.values()) {
      if (serviceDefinitionType.yamlName.equalsIgnoreCase(yamlName)) {
        return serviceDefinitionType;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", yamlName, Arrays.toString(ServiceDefinitionType.values())));
  }

  ServiceDefinitionType(String displayName, List<ExecutionStrategyType> executionStrategies, String yamlName) {
    this.displayName = displayName;
    this.executionStrategies = executionStrategies;
    this.yamlName = yamlName;
  }

  public static List<ExecutionStrategyType> getExecutionStrategies(ServiceDefinitionType serviceDefinitionType) {
    return serviceDefinitionType.executionStrategies;
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  public static ServiceDefinitionType fromString(final String s) {
    return ServiceDefinitionType.getServiceDefinitionType(s);
  }
}
