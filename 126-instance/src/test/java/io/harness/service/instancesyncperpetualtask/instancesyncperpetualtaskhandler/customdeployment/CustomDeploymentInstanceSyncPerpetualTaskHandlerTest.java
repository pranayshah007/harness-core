/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customdeployment;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customDeployment.CustomDeploymentInstanceSyncPerpetualTaskHandler.OUTPUT_PATH_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customDeployment.CustomDeploymentInstanceSyncPerpetualTaskHandler;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CustomDeploymentInstanceSyncPerpetualTaskHandlerTest extends InstancesTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String SCRIPT = "script";
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks CustomDeploymentInstanceSyncPerpetualTaskHandler customDeploymentInstanceSyncPerpetualTaskHandler;
  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetExecutionBundle() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .infrastructureKind("CustomDeployment")
                                                            .connectorRef("connector")
                                                            .envIdentifier("env")
                                                            .serviceIdentifier("service")
                                                            .infrastructureKey("key")
                                                            .build();

    DeploymentInfoDTO deploymentInfoDTO =
        CustomDeploymentNGDeploymentInfoDTO.builder().instanceFetchScript(SCRIPT).infratructureKey("key").build();
    List<DeploymentInfoDTO> deploymentInfoDTOList = Collections.singletonList(deploymentInfoDTO);
    Map<String, String> attributes = new HashMap<>();
    attributes.put("instanceName", "hostName");
    InfrastructureOutcome infrastructureOutcome = CustomDeploymentInfrastructureOutcome.builder()
                                                      .instancesListPath("path")
                                                      .instanceAttributes(attributes)
                                                      .build();
    CustomDeploymentNGInstanceSyncPerpetualTaskParams customDeploymentNGInstanceSyncPerpetualTaskParams =
        CustomDeploymentNGInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_IDENTIFIER)
            .setInstancesListPath("path")
            .setOutputPathKey(OUTPUT_PATH_KEY)
            .putAllInstanceAttributes(attributes)
            .setInfrastructureKey("key")
            .setScript(SCRIPT)
            .build();
    List<ExecutionCapability> expectedExecutionCapabilityList = new ArrayList<>();
    expectedExecutionCapabilityList.add(SelectorCapability.builder().build());
    byte[] bytes3 = {72};
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    expectedExecutionCapabilityList.forEach(executionCapability -> builder.build());
    PerpetualTaskExecutionBundle expectedPerpetualTaskExecutionBundle =
        builder.setTaskParams(Any.pack(customDeploymentNGInstanceSyncPerpetualTaskParams))
            .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, ORG_IDENTIFIER + "/" + PROJECT_IDENTIFIER))
            .build();
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle =
        customDeploymentInstanceSyncPerpetualTaskHandler.getExecutionBundle(
            infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);
    assertThat(perpetualTaskExecutionBundle).isEqualTo(expectedPerpetualTaskExecutionBundle);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetExecutionBundleWithDelegateTags() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .infrastructureKind("CustomDeployment")
                                                            .connectorRef("connector")
                                                            .envIdentifier("env")
                                                            .serviceIdentifier("service")
                                                            .infrastructureKey("infraKey")
                                                            .build();

    List<String> tags = Arrays.asList("tag1", "tag2");
    DeploymentInfoDTO deploymentInfoDTO = CustomDeploymentNGDeploymentInfoDTO.builder()
                                              .instanceFetchScript(SCRIPT)
                                              .tags(tags)
                                              .infratructureKey("infraKey")
                                              .build();
    Map<String, String> attributes = new HashMap<>();
    attributes.put("instanceName", "hostName");
    InfrastructureOutcome infrastructureOutcome = CustomDeploymentInfrastructureOutcome.builder()
                                                      .instancesListPath("path")
                                                      .instanceAttributes(attributes)
                                                      .build();

    CustomDeploymentNGInstanceSyncPerpetualTaskParams customDeploymentNGInstanceSyncPerpetualTaskParams =
        CustomDeploymentNGInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_IDENTIFIER)
            .setInstancesListPath("path")
            .setOutputPathKey(OUTPUT_PATH_KEY)
            .putAllInstanceAttributes(attributes)
            .setInfrastructureKey("infraKey")
            .setScript(SCRIPT)
            .build();
    List<ExecutionCapability> expectedExecutionCapabilityList = new ArrayList<>();
    expectedExecutionCapabilityList.add(SelectorCapability.builder().selectors(new HashSet<>(tags)).build());
    byte[] bytes3 = {72};
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();

    expectedExecutionCapabilityList.forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder().setKryoCapability(ByteString.copyFrom(new byte[] {'a'})).build())
               .build());

    PerpetualTaskExecutionBundle expectedPerpetualTaskExecutionBundle =
        builder.setTaskParams(Any.pack(customDeploymentNGInstanceSyncPerpetualTaskParams))
            .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, ORG_IDENTIFIER + "/" + PROJECT_IDENTIFIER))
            .build();

    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[] {'a'});
    when(delegateServiceGrpcClient.isTaskTypeSupported(any(), any())).thenReturn(false);

    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle =
        customDeploymentInstanceSyncPerpetualTaskHandler.getExecutionBundle(
            infrastructureMappingDTO, Collections.singletonList(deploymentInfoDTO), infrastructureOutcome);
    assertThat(perpetualTaskExecutionBundle).isEqualTo(expectedPerpetualTaskExecutionBundle);
  }
}
