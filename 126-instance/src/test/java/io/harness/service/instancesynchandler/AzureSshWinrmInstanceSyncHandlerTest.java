/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AzureSshWinrmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.models.infrastructuredetails.AzureSshWinrmInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDP)
public class AzureSshWinrmInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String INFRASTRUCTURE_KEY = "INFRA_KEY";
  private static final String HOST = "HOST";
  private static final String SSH_SERVICE = ServiceSpecType.SSH;
  private ServerInstanceInfo serverInstanceInfo = AzureSshWinrmServerInstanceInfo.builder()
                                                      .serviceType(SSH_SERVICE)
                                                      .host(HOST)
                                                      .infrastructureKey(INFRASTRUCTURE_KEY)
                                                      .build();
  @InjectMocks public AzureSshWinrmInstanceSyncHandler azureSshWinrmInstanceSyncHandler;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetails() {
    AzureSshWinrmInstanceInfoDTO instanceInfoDTO =
        AzureSshWinrmInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST).build();
    InfrastructureDetails infrastructureDetails =
        azureSshWinrmInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(infrastructureDetails).isInstanceOf(AzureSshWinrmInfrastructureDetails.class);
    assertThat(((AzureSshWinrmInfrastructureDetails) infrastructureDetails).getHost()).isEqualTo(HOST);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    InstanceInfoDTO instanceInfoDTO =
        azureSshWinrmInstanceSyncHandler.getInstanceInfoForServerInstance(serverInstanceInfo);
    assertThat(instanceInfoDTO).isInstanceOf(AzureSshWinrmInstanceInfoDTO.class);
    assertThat(((AzureSshWinrmInstanceInfoDTO) instanceInfoDTO).getHost()).isEqualTo(HOST);
    assertThat(((AzureSshWinrmInstanceInfoDTO) instanceInfoDTO).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);
    assertThat(((AzureSshWinrmInstanceInfoDTO) instanceInfoDTO).getServiceType()).isEqualTo(SSH_SERVICE);

    InfrastructureDetails infrastructureDetails =
        azureSshWinrmInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(infrastructureDetails).isInstanceOf(AzureSshWinrmInfrastructureDetails.class);
    assertThat(((AzureSshWinrmInfrastructureDetails) infrastructureDetails).getHost()).isEqualTo(HOST);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    SshWinRmAzureInfrastructureOutcome outcome =
        SshWinRmAzureInfrastructureOutcome.builder().infrastructureKey(INFRASTRUCTURE_KEY).build();
    DeploymentInfoDTO deploymentInfo =
        azureSshWinrmInstanceSyncHandler.getDeploymentInfo(outcome, Collections.singletonList(serverInstanceInfo));
    assertThat(deploymentInfo).isInstanceOf(AzureSshWinrmDeploymentInfoDTO.class);
    assertThat(deploymentInfo.getType()).isEqualTo(SSH_SERVICE);
    assertThat(((AzureSshWinrmDeploymentInfoDTO) deploymentInfo).getHost()).isEqualTo(HOST);
    assertThat(((AzureSshWinrmDeploymentInfoDTO) deploymentInfo).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);
  }
}
