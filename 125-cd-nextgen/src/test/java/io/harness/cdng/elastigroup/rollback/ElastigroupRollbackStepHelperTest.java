/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.rollback;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.ElastigroupEntityHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupPreFetchOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class ElastigroupRollbackStepHelperTest extends CDNGTestBase {
  @Mock private ElastigroupEntityHelper entityHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private StageExecutionInfoService stageExecutionInfoService;

  @InjectMocks @Spy private ElastigroupRollbackStepHelper elastigroupRollbackStepHelper;

  String elastigroupNamePrefix = "test-app";
  String awsRegion = "us-east-1";

  @Before
  public void setup() {
    doReturn(ConnectorInfoDTO.builder().build()).when(entityHelper).getConnectorInfoDTO(anyString(), any());
    doReturn(Collections.emptyList()).when(entityHelper).getEncryptionDataDetails(any(), any());
  }

  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void getElastigroupRollbackTaskParametersTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    InfrastructureOutcome infrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().connectorRef("some-connector").build();
    doReturn(infrastructureOutcome).when(elastigroupRollbackStepHelper).getInfrastructureOutcome(any());
    doReturn(ConnectorInfoDTO.builder().build()).when(elastigroupRollbackStepHelper).getConnector(anyString(), any());

    final List<ElastiGroup> prevElastigroups = Arrays.asList(ElastiGroup.builder().name("eg").build());

    ElastigroupPreFetchOutcome elastigroupPreFetchOutcome = ElastigroupPreFetchOutcome.builder()
                                                                .blueGreen(true)
                                                                .elastigroups(prevElastigroups)
                                                                .elastigroupNamePrefix(elastigroupNamePrefix)
                                                                .build();
    doReturn(elastigroupPreFetchOutcome).when(elastigroupRollbackStepHelper).getElastigroupPreFetchOutcome(any());

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
        ElastigroupSetupDataOutcome.builder()
            .awsRegion(awsRegion)
            .awsConnectorRef("connector")
            .oldElastigroupOriginalConfig(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build())
            .newElastigroupOriginalConfig(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build())
            .build();
    doReturn(elastigroupSetupDataOutcome).when(elastigroupRollbackStepHelper).getElastigroupSetupOutcome(any());

    ElastigroupRollbackTaskParameters result =
        elastigroupRollbackStepHelper.getElastigroupRollbackTaskParameters(null, ambiance, stepElementParameters);

    assertThat(result.getElastigroupNamePrefix()).isEqualTo(elastigroupNamePrefix);
    assertThat(result.getAwsRegion()).isEqualTo(awsRegion);
    assertThat(result.getPrevElastigroups()).isEqualTo(prevElastigroups);
    assertThat(result.getOldElastigroup()).isNotNull();
    assertThat(result.getNewElastigroup()).isNotNull();
  }
}
