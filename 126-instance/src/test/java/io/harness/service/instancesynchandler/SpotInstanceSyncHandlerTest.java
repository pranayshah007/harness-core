/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.SpotDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.SpotInstanceInfoDTO;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.SpotInfrastructureDetails;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDP)
public class SpotInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String INFRASTRUCTURE_KEY = "INFRA_KEY";
  private static final String ELASTIGROUP_ID = "elastigroupId";
  private static final String ELASTIGROUP_SERVICE = ServiceSpecType.ELASTIGROUP;
  private static final String EC2_INSTANCE_ID = "ec2InstanceId";

  private ServerInstanceInfo serverInstanceInfo = SpotServerInstanceInfo.builder()
                                                      .elastigroupId(ELASTIGROUP_ID)
                                                      .infrastructureKey(INFRASTRUCTURE_KEY)
                                                      .ec2InstanceId(EC2_INSTANCE_ID)
                                                      .build();
  @InjectMocks public SpotInstanceSyncHandler spotInstanceSyncHandler;

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetInfrastructureDetails() {
    SpotInstanceInfoDTO instanceInfoDTO = SpotInstanceInfoDTO.builder()
                                              .infrastructureKey(INFRASTRUCTURE_KEY)
                                              .elastigroupId(ELASTIGROUP_ID)
                                              .ec2InstanceId(EC2_INSTANCE_ID)
                                              .build();
    InfrastructureDetails infrastructureDetails = spotInstanceSyncHandler.getInfrastructureDetails(instanceInfoDTO);
    assertThat(((SpotInfrastructureDetails) infrastructureDetails).getEc2InstanceId()).isEqualTo(EC2_INSTANCE_ID);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    InstanceInfoDTO instanceInfoDTO = spotInstanceSyncHandler.getInstanceInfoForServerInstance(serverInstanceInfo);
    assertThat(((SpotInstanceInfoDTO) instanceInfoDTO).getElastigroupId()).isEqualTo(ELASTIGROUP_ID);
    assertThat(((SpotInstanceInfoDTO) instanceInfoDTO).getEc2InstanceId()).isEqualTo(EC2_INSTANCE_ID);
    assertThat(((SpotInstanceInfoDTO) instanceInfoDTO).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    ElastigroupInfrastructureOutcome outcome =
        ElastigroupInfrastructureOutcome.builder().infrastructureKey(INFRASTRUCTURE_KEY).build();
    DeploymentInfoDTO deploymentInfo =
        spotInstanceSyncHandler.getDeploymentInfo(outcome, Collections.singletonList(serverInstanceInfo));
    assertThat(deploymentInfo.getType()).isEqualTo(ELASTIGROUP_SERVICE);
    assertThat(((SpotDeploymentInfoDTO) deploymentInfo).getInfrastructureKey()).isEqualTo(INFRASTRUCTURE_KEY);

    Map<String, Set<String>> elastigroupEc2InstancesMap =
        ((SpotDeploymentInfoDTO) deploymentInfo).getElastigroupEc2InstancesMap();
    assertThat(elastigroupEc2InstancesMap.size()).isEqualTo(1);
    assertThat(elastigroupEc2InstancesMap.get(ELASTIGROUP_ID)).contains(EC2_INSTANCE_ID);
  }
}
