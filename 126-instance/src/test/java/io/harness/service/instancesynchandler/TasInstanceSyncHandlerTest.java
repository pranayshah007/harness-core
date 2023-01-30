/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.TasDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.EcsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.TasInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.TasInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class TasInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String APP_NAME = "App";
  private static final String SPACE = "space";

  @InjectMocks private TasInstanceSyncHandler tasInstanceSyncHandler;
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetails() {
    InstanceInfoDTO instanceInfoDTO = TasInstanceInfoDTO.builder().tasApplicationName(APP_NAME).build();
    TasInfrastructureDetails infrastructureDetails =
        (TasInfrastructureDetails) tasInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(infrastructureDetails.getTasApplicationName()).isEqualTo(APP_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetailsError() {
    InstanceInfoDTO instanceInfoDTO = EcsInstanceInfoDTO.builder().build();
    assertThatThrownBy(() -> tasInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoError() {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().infrastructureKey("key").build();
    EcsServerInstanceInfo ecsServerInstanceInfo = EcsServerInstanceInfo.builder().region("east").build();

    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(ecsServerInstanceInfo);

    assertThatThrownBy(() -> tasInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    TasInstanceInfoDTO instanceInfoForServerInstance =
        (TasInstanceInfoDTO) tasInstanceSyncHandler.getInstanceInfoForServerInstance(
            TasServerInstanceInfo.builder().space(SPACE).tasApplicationName(APP_NAME).build());

    assertThat(instanceInfoForServerInstance.getSpace()).isEqualTo(SPACE);
    assertThat(instanceInfoForServerInstance.getTasApplicationName()).isEqualTo(APP_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstanceWithInvalidArgument() {
    assertThatThrownBy(()
                           -> tasInstanceSyncHandler.getInstanceInfoForServerInstance(
                               NativeHelmServerInstanceInfo.builder().podName("pod").build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().infrastructureKey("key").build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder().tasApplicationName(APP_NAME).build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(tasServerInstanceInfo);
    TasDeploymentInfoDTO deploymentInfo =
        (TasDeploymentInfoDTO) tasInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos);
    assertThat(deploymentInfo.getApplicationName()).isEqualTo(APP_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoWithEmptyServerList() {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().infrastructureKey("key").build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    assertThatThrownBy(() -> tasInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetDeploymentInfoWithInvalidOutcome() {
    K8sDirectInfrastructureOutcome infrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().infrastructureKey("key").build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder().tasApplicationName(APP_NAME).build();
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    serverInstanceInfos.add(tasServerInstanceInfo);
    assertThatThrownBy(() -> tasInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
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
    assertThatThrownBy(() -> tasInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfos))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testUpdateInstance() {
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    TasInstanceInfoDTO instanceInfoFromServer = TasInstanceInfoDTO.builder().tasApplicationName(APP_NAME).build();
    InstanceDTO updatedInstanceDTO = tasInstanceSyncHandler.updateInstance(instanceDTO, instanceInfoFromServer);
    assertThat(updatedInstanceDTO.getInstanceInfoDTO()).isEqualTo(instanceInfoFromServer);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInfrastructureMappingType() {
    String infrastructureMappingType = tasInstanceSyncHandler.getInfrastructureKind();
    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.TAS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetPerpetualTaskType() {
    String perpetualTaskType = tasInstanceSyncHandler.getPerpetualTaskType();
    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.TAS_INSTANCE_SYNC_NG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetInstanceType() {
    InstanceType instanceType = tasInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.TAS_INSTANCE);
  }
}
