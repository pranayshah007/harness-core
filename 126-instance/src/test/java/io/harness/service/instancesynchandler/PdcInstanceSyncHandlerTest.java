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
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.PdcDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.PdcInstanceInfoDTO;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.PdcInfrastructureDetails;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDP)
public class PdcInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String INFRASTRUCTURE_KEY = "INFRA_KEY";
  private static final String HOST = "HOST";
  private static final String SSH_SERVICE = ServiceSpecType.SSH;
  private ServerInstanceInfo serverInstanceInfo =
      PdcServerInstanceInfo.builder().serviceType(SSH_SERVICE).host(HOST).infrastructureKey(INFRASTRUCTURE_KEY).build();
  @InjectMocks public PdcInstanceSyncHandler pdcInstanceSyncHandler;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetails() {
    PdcInstanceInfoDTO instanceInfoDTO =
        PdcInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST).build();
    InfrastructureDetails infrastructureDetails = pdcInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(infrastructureDetails).isInstanceOf(PdcInfrastructureDetails.class);
    assertThat(((PdcInfrastructureDetails) infrastructureDetails).getHost()).isEqualTo(HOST);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    InstanceInfoDTO instanceInfoDTO = pdcInstanceSyncHandler.getInstanceInfoForServerInstance(serverInstanceInfo);
    assertThat(instanceInfoDTO).isInstanceOf(PdcInstanceInfoDTO.class);
    assertThat(((PdcInstanceInfoDTO) instanceInfoDTO).getHost()).isEqualTo(HOST);
    assertThat(((PdcInstanceInfoDTO) instanceInfoDTO).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);
    assertThat(((PdcInstanceInfoDTO) instanceInfoDTO).getServiceType()).isEqualTo(SSH_SERVICE);

    InfrastructureDetails infrastructureDetails = pdcInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(infrastructureDetails).isInstanceOf(PdcInfrastructureDetails.class);
    assertThat(((PdcInfrastructureDetails) infrastructureDetails).getHost()).isEqualTo(HOST);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    PdcInfrastructureOutcome pdcInfrastructureOutcome =
        PdcInfrastructureOutcome.builder().infrastructureKey(INFRASTRUCTURE_KEY).build();
    DeploymentInfoDTO deploymentInfo = pdcInstanceSyncHandler.getDeploymentInfo(
        pdcInfrastructureOutcome, Collections.singletonList(serverInstanceInfo));
    assertThat(deploymentInfo).isInstanceOf(PdcDeploymentInfoDTO.class);
    assertThat(deploymentInfo.getType()).isEqualTo(SSH_SERVICE);
    assertThat(((PdcDeploymentInfoDTO) deploymentInfo).getHost()).isEqualTo(HOST);
    assertThat(((PdcDeploymentInfoDTO) deploymentInfo).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);
  }
}
