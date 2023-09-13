/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.CustomDeploymentOutcomeMetadata;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.dtos.instanceinfo.EcsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.CustomDeploymentInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class CustomDeploymentInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String INSTANCE_NAME = "instanceName";
  private static final String SCRIPT = "Script";
  @InjectMocks private CustomDeploymentInstanceSyncHandler customDeploymentInstanceSyncHandler;
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetails() {
    InstanceInfoDTO instanceInfoDTO = CustomDeploymentInstanceInfoDTO.builder().instanceName(INSTANCE_NAME).build();
    CustomDeploymentInfrastructureDetails infrastructureDetails =
        (CustomDeploymentInfrastructureDetails) customDeploymentInstanceSyncHandler.getInfrastructureDetails(
            instanceInfoDTO);
    assertThat(infrastructureDetails.getInstanceName()).isEqualTo(INSTANCE_NAME);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetailsError() {
    InstanceInfoDTO instanceInfoDTO = EcsInstanceInfoDTO.builder().build();
    assertThatThrownBy(() -> customDeploymentInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoError() {
    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder().infrastructureKey("key").build();
    EcsServerInstanceInfo customDeploymentServerInstanceInfo = EcsServerInstanceInfo.builder().region("east").build();

    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(customDeploymentServerInstanceInfo);

    assertThatThrownBy(
        () -> customDeploymentInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    Map<String, Object> property = new HashMap<>();
    property.put("hostName", "hostName");
    CustomDeploymentInstanceInfoDTO instanceInfoForServerInstance =
        (CustomDeploymentInstanceInfoDTO) customDeploymentInstanceSyncHandler.getInstanceInfoForServerInstance(
            CustomDeploymentServerInstanceInfo.builder()
                .instanceId("Id")
                .instanceName(INSTANCE_NAME)
                .instanceFetchScript(SCRIPT)
                .properties(property)
                .build());

    assertThat(instanceInfoForServerInstance.getInstanceName()).isEqualTo(INSTANCE_NAME);
    assertThat(instanceInfoForServerInstance.getProperties()).isEqualTo(property);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstanceWithInvalidArgument() {
    assertThatThrownBy(()
                           -> customDeploymentInstanceSyncHandler.getInstanceInfoForServerInstance(
                               NativeHelmServerInstanceInfo.builder().podName("pod").build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder().infrastructureKey("key").build();
    CustomDeploymentServerInstanceInfo customDeploymentServerInstanceInfo =
        CustomDeploymentServerInstanceInfo.builder().instanceFetchScript(SCRIPT).build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(customDeploymentServerInstanceInfo);
    CustomDeploymentNGDeploymentInfoDTO deploymentInfo =
        (CustomDeploymentNGDeploymentInfoDTO) customDeploymentInstanceSyncHandler.getDeploymentInfo(
            infrastructureOutcome, serverInstanceInfos);
    assertThat(deploymentInfo.getInfratructureKey()).isEqualTo("key");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoWithEmptyServerList() {
    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder().infrastructureKey("key").build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    assertThatThrownBy(
        () -> customDeploymentInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoWithInvalidOutcome() {
    K8sDirectInfrastructureOutcome infrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().infrastructureKey("key").build();
    CustomDeploymentServerInstanceInfo customDeploymentServerInstanceInfo =
        CustomDeploymentServerInstanceInfo.builder().instanceFetchScript(SCRIPT).build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(customDeploymentServerInstanceInfo);
    assertThatThrownBy(
        () -> customDeploymentInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoWithInvalidServerInstance() {
    K8sDirectInfrastructureOutcome infrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().infrastructureKey("key").build();
    K8sServerInstanceInfo serverInstanceInfo = K8sServerInstanceInfo.builder().podIP("podIp").build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(serverInstanceInfo);
    assertThatThrownBy(
        () -> customDeploymentInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testUpdateInstance() {
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    CustomDeploymentInstanceInfoDTO instanceInfoFromServer =
        CustomDeploymentInstanceInfoDTO.builder().instanceName(INSTANCE_NAME).build();
    InstanceDTO updatedInstanceDTO =
        customDeploymentInstanceSyncHandler.updateInstance(instanceDTO, instanceInfoFromServer);
    assertThat(updatedInstanceDTO.getInstanceInfoDTO()).isEqualTo(instanceInfoFromServer);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInfrastructureMappingType() {
    String infrastructureMappingType = customDeploymentInstanceSyncHandler.getInfrastructureKind();
    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.CUSTOM_DEPLOYMENT);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetPerpetualTaskType() {
    String perpetualTaskType = customDeploymentInstanceSyncHandler.getPerpetualTaskType();
    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC_NG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceType() {
    InstanceType instanceType = customDeploymentInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.CUSTOM_DEPLOYMENT_INSTANCE);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testUpdateDeploymentInfo() {
    CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO =
        CustomDeploymentNGDeploymentInfoDTO.builder().infratructureKey("key").instanceFetchScript("fetch").build();
    CustomDeploymentOutcomeMetadata customDeploymentOutcomeMetadata =
        CustomDeploymentOutcomeMetadata.builder().instanceFetchScript("script").build();
    EcsServerInstanceInfo customDeploymentServerInstanceInfo = EcsServerInstanceInfo.builder().region("east").build();

    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(customDeploymentServerInstanceInfo);

    customDeploymentNGDeploymentInfoDTO =
        (CustomDeploymentNGDeploymentInfoDTO) customDeploymentInstanceSyncHandler.updateDeploymentInfoDTO(
            customDeploymentNGDeploymentInfoDTO, customDeploymentOutcomeMetadata);
    assertThat(customDeploymentNGDeploymentInfoDTO.getInstanceFetchScript()).isEqualTo("script");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testUpdateDeploymentInfoError() {
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO =
        K8sDeploymentInfoDTO.builder().namespaces(new LinkedHashSet<>()).releaseName("name").build();
    CustomDeploymentOutcomeMetadata customDeploymentOutcomeMetadata =
        CustomDeploymentOutcomeMetadata.builder().instanceFetchScript("script").build();
    EcsServerInstanceInfo customDeploymentServerInstanceInfo = EcsServerInstanceInfo.builder().region("east").build();

    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(customDeploymentServerInstanceInfo);

    assertThatThrownBy(()
                           -> customDeploymentInstanceSyncHandler.updateDeploymentInfoDTO(
                               k8sDeploymentInfoDTO, customDeploymentOutcomeMetadata))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
