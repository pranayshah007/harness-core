/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.datagen;

import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.ComputingResource;
import io.harness.delegate.EmptyDirVolume;
import io.harness.delegate.ExecutionInfrastructure;
import io.harness.delegate.K8sInfraSpec;
import io.harness.delegate.LogConfig;
import io.harness.delegate.Resource;
import io.harness.delegate.ResourceType;
import io.harness.delegate.SchedulingConfig;
import io.harness.delegate.SecurityContext;
import io.harness.delegate.SetupExecutionInfrastructureRequest;
import io.harness.delegate.StepSpec;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.RunnerType;
import io.harness.delegate.core.beans.ExecutionMode;
import io.harness.delegate.core.beans.ExecutionPriority;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.core.beans.PluginSource;
import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.delegate.core.beans.StepRuntime;

import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InfraRequestTestFactory {
  public static SetupExecutionInfrastructureRequest createRequest(
      final String logKey, final StepSpec step1, final StepSpec step2) {
    final K8sInfraSpec infraSpec = createK8sInfraSpec(step1, step2);
    return createSetupInfraRequest(infraSpec, logKey);
  }

  public static K8sInfraSpec createK8sInfraSpec(final StepSpec step1, final StepSpec step2) {
    return K8sInfraSpec.newBuilder()
        .setComputeResource(createCompute())
        .setSecurityContext(createSecurityContext())
        .addResources(createEmptyDir())
        .addSteps(step1)
        .addSteps(step2)
        .build();
  }

  public static StepSpec createStep(final String step1, final String image) {
    return StepSpec.newBuilder()
        .setStepId(step1)
        .setImage(image)
        .addArgs("arg1")
        .addCommands("command1")
        .setComputeResource(createCompute())
        .setSecurityContext(createSecurityContext())
        .putEnv("env1", "value1")
        .putEnv("env2", "value2")
        .addPorts(8080)
        .build();
  }

  // Creating expectations
  public static K8SStep expectedStep(final String taskId, final String image) {
    final StepRuntime runtime = expectedRuntime(image);
    return K8SStep.newBuilder()
        .setId(taskId)
        .setMode(ExecutionMode.MODE_ONCE)
        .setPriority(ExecutionPriority.PRIORITY_DEFAULT)
        .setRuntime(runtime)
        .build();
  }

  public static K8SInfra expectedInfra(
      final String logKey, final String loggingToken, final K8SStep task1, final K8SStep task2) {
    return K8SInfra.newBuilder()
        .addSteps(task1)
        .addSteps(task2)
        .setCompute(expectedCompute())
        .addResources(expectedEmptyDir())
        .setWorkingDir("/opt/harness")
        .setLogPrefix(logKey)
        .setLogToken(loggingToken)
        .setSecurityContext(expectedSecurityContext())
        .build();
  }

  private static SetupExecutionInfrastructureRequest createSetupInfraRequest(
      final K8sInfraSpec infraSpec, final String logKey) {
    final ExecutionInfrastructure infra = ExecutionInfrastructure.newBuilder()
                                              .setLogConfig(LogConfig.newBuilder().setLogKey(logKey).build())
                                              .setK8S(infraSpec)
                                              .build();
    final var config = SchedulingConfig.newBuilder()
                           .setAccountId("accountId")
                           .setCallbackToken(DelegateCallbackToken.newBuilder().setToken("callbackToken").build())
                           .setExecutionTimeout(Duration.newBuilder().setSeconds(100).build())
                           .addSelectors(TaskSelector.newBuilder().build())
                           .addSelectors(TaskSelector.newBuilder().build())
                           .setRunnerType(RunnerType.RUNNER_TYPE_K8S)
                           .build();

    return SetupExecutionInfrastructureRequest.newBuilder().setInfra(infra).setConfig(config).build();
  }

  private static Resource createEmptyDir() {
    return Resource.newBuilder()
        .setType(ResourceType.RES_VOLUME)
        .setEmptyDir(
            EmptyDirVolume.newBuilder().setName("name").setPath("path").setSize("size").setMedium("medium").build())
        .build();
  }

  private static ComputingResource createCompute() {
    return ComputingResource.newBuilder().setCpu("3").setMemory("4").build();
  }

  private static SecurityContext createSecurityContext() {
    return SecurityContext.newBuilder()
        .setRunAsUser(11)
        .setRunAsGroup(12)
        .setPrivileged(true)
        .setProcMount("procMount")
        .setReadOnlyRootFilesystem(true)
        .setRunAsNonRoot(true)
        .setAllowPrivilegeEscalation(true)
        .addAddCapability("add1")
        .addAddCapability("add2")
        .addDropCapability("drop1")
        .addDropCapability("drop2")
        .build();
  }

  private static ResourceRequirements expectedCompute() {
    return ResourceRequirements.newBuilder().setCpu("3").setMemory("4").build();
  }

  private static io.harness.delegate.core.beans.Resource expectedEmptyDir() {
    return io.harness.delegate.core.beans.Resource.newBuilder()
        .setType(io.harness.delegate.core.beans.ResourceType.RES_VOLUME)
        .setSpec(Any.pack(io.harness.delegate.core.beans.EmptyDirVolume.newBuilder()
                              .setName("name")
                              .setPath("path")
                              .setSize("size")
                              .setMedium("medium")
                              .build()))
        .build();
  }

  private static StepRuntime expectedRuntime(final String image) {
    return StepRuntime.newBuilder()
        .setCompute(expectedCompute())
        .setSource(PluginSource.SOURCE_IMAGE)
        .setUses(image)
        .addCommand("command1")
        .addArg("arg1")
        .setSecurityContext(expectedSecurityContext())
        .putEnv("env1", "value1")
        .putEnv("env2", "value2")
        .build();
  }

  private static io.harness.delegate.core.beans.SecurityContext expectedSecurityContext() {
    return io.harness.delegate.core.beans.SecurityContext.newBuilder()
        .setRunAsUser(11)
        .setRunAsGroup(12)
        .setPrivileged(true)
        .setProcMount("procMount")
        .setReadOnlyRootFilesystem(true)
        .setRunAsNonRoot(true)
        .setAllowPrivilegeEscalation(true)
        .addAddCapability("add1")
        .addAddCapability("add2")
        .addDropCapability("drop1")
        .addDropCapability("drop2")
        .build();
  }
}
