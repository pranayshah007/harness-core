/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.stages;

import static io.harness.rule.OwnerRule.SAHITHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class IntegrationStageStepParametersPMSTest extends CIExecutionTestBase {
  IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
      IntegrationStageStepParametersPMS.builder().build();

  @Mock private Infrastructure infrastructure;

  @Mock private YamlField yamlField;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenPreviousStageWasNotSelectedForExecution() throws IOException {
    Mockito.mockStatic(PlanCreatorUtils.class);
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder()
            .integrationStageConfig(IntegrationStageConfigImpl.builder()
                                        .infrastructure(UseFromStageInfraYaml.builder().useFromStage("build").build())
                                        .build())
            .type(IntegrationStageNode.StepType.CI)
            .identifier("build2")
            .build();

    String yaml =
        "{\"pipeline\":{\"identifier\":\"CI8379sub\",\"name\":\"CI-8379-sub\",\"projectIdentifier\":\"SahithiProject\",\"orgIdentifier\":\"default\",\"tags\":{\"__uuid\":\"MUtQhjeVQK2txVO2q33fsA\"},\"stages\":[{\"stage\":{\"identifier\":\"build2\",\"type\":\"CI\",\"name\":\"build2\",\"description\":\"\",\"spec\":{\"cloneCodebase\":false,\"infrastructure\":{\"useFromStage\":\"build\",\"__uuid\":\"r5ehh61oRj-N7h1VP9MnKQ\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"dUlC3x3JSNGgk0s2fXlT8g\"},\"__uuid\":\"7_ilmaRoTHGMpxiJez7q_A\"},\"__uuid\":\"77rHyyO6TxCnl9wr2_KJ1Q\"}],\"__uuid\":\"w17FG42vRFa4lwrkBTPM4w\"},\"__uuid\":\"gViwJM4YQ3qL6NsZ98UX-Q\"},\"__uuid\":\"9ek8HGngTMOmTvfUQrboeA\"},\"__uuid\":\"GgmtiF8ITFuXiP-Lq_xj5g\"},{\"stage\":{\"name\":\"Pipeline Rollback\",\"identifier\":\"prb-jCRLDTPKRDKbhcF41te1vQ\",\"type\":\"PipelineRollback\",\"spec\":{\"__uuid\":\"XRFgxJkHTUuLZ9yZwzT6_Q\"},\"__uuid\":\"uHuIcYSCS2a-XD_BBIVloQ\"},\"__uuid\":\"ix3qqUQWTUaOby-q2gO2GQ\"}],\"allowStageExecutions\":true,\"properties\":{\"ci\":{\"codebase\":{\"connectorRef\":\"GitConnector\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"main\",\"__uuid\":\"0l-Ko1g_RFacfQOitLDLng\"},\"__uuid\":\"A0pEwUmiT5CxMLz3Hx3fEw\"},\"__uuid\":\"8EBVmjraQP2tHyOsy6sRew\"},\"__uuid\":\"-Yqog7ftSMCFVXfd_lg1Pw\"},\"__uuid\":\"OMQxnvRmRoWIibFCZkKYmg\"},\"__uuid\":\"MRAg3O7mT5OszT_rfrWylw\"},\"__uuid\":\"w_nMNvsUQFOKg8qfMPTEEw\"}";

    ObjectMapper mapper = new ObjectMapper();
    String pipelineYaml =
        "{\"identifier\":\"build2\",\"type\":\"CI\",\"name\":\"build2\",\"description\":\"\",\"spec\":{\"cloneCodebase\":false,\"infrastructure\":{\"useFromStage\":\"build\",\"__uuid\":\"ZUlshyaWQLiDeBWcLBW1cQ\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"0LRwUUsHT3C82eqz60BPKw\"},\"__uuid\":\"_93bWUtqRf2eKlbhWDKHEw\"},\"__uuid\":\"UQH5KkVARxCCqJU-lXoXeQ\"}],\"__uuid\":\"jVZnywtxRvSh4yWZ4o_gXA\"},\"__uuid\":\"MqgG3Ic5Ti6U5RzqHeUnlA\"},\"__uuid\":\"7UdLGSeHSu-ScyLJUgkmgw\"}";
    JsonNode rootJsonNode = mapper.readTree(pipelineYaml);
    YamlNode parentYamlNode = new YamlNode("name", rootJsonNode);
    YamlNode rootYamlNode = new YamlNode("name1", rootJsonNode, parentYamlNode);

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(new YamlField(rootYamlNode)).yaml(yaml).build();
    Infrastructure infrastructure = IntegrationStageStepParametersPMS.getInfrastructure(stageNode, ctx);

    assertThatThrownBy(() -> IntegrationStageStepParametersPMS.getInfrastructure(stageNode, ctx))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining(
            "Stage build2 has useFromStage dependency on Stage build. Please select the Stage build to run build2 ");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  @SneakyThrows
  public void shouldReturnInfrastructure() {
    Mockito.mockStatic(PlanCreatorUtils.class);
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder()
            .integrationStageConfig(IntegrationStageConfigImpl.builder()
                                        .infrastructure(UseFromStageInfraYaml.builder().useFromStage("build").build())
                                        .build())
            .type(IntegrationStageNode.StepType.CI)
            .identifier("build2")
            .build();
    String yaml =
        "{\"pipeline\":{\"identifier\":\"CI8379sub\",\"name\":\"CI-8379-sub\",\"projectIdentifier\":\"SahithiProject\",\"orgIdentifier\":\"default\",\"tags\":{\"__uuid\":\"Eyfh6767S2OJqgY_dJ6aZQ\"},\"stages\":[{\"stage\":{\"identifier\":\"build\",\"type\":\"CI\",\"name\":\"build\",\"spec\":{\"cloneCodebase\":true,\"caching\":{\"enabled\":false,\"__uuid\":\"t3JiG0dvRhCFUCExlVyU1Q\"},\"infrastructure\":{\"type\":\"KubernetesDirect\",\"spec\":{\"connectorRef\":\"account.SahithiK8\",\"namespace\":\"harness-delegate-ng\",\"automountServiceAccountToken\":true,\"nodeSelector\":{\"__uuid\":\"-IXGDh_XSkGHbapfdTJ-Bw\"},\"os\":\"Linux\",\"__uuid\":\"5tbdnwX2T2S0rQFeVrY9nQ\"},\"__uuid\":\"Inexl2QyR2y8Zk_W-4E2Mw\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"LMDQPMaoRsGQuvZr72UUpQ\"},\"__uuid\":\"FeoyK2V_R86TFBrYfuZbRQ\"},\"__uuid\":\"wbZUHG8STbGAdgMmVVHO8Q\"}],\"__uuid\":\"9aW1TQz1R1ajSVKGv3gf4g\"},\"__uuid\":\"BkLaCGvnRs-DWBHsOlhBlw\"},\"__uuid\":\"BX8q6o77RuGZ3gwupb705g\"},\"__uuid\":\"KfhkFhuLSWWABS2oQ5B6Ig\"},{\"stage\":{\"identifier\":\"build2\",\"type\":\"CI\",\"name\":\"build2\",\"description\":\"\",\"spec\":{\"cloneCodebase\":false,\"infrastructure\":{\"useFromStage\":\"build\",\"__uuid\":\"9ZC57TNjRLWDxODBTZfAIg\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"_fUuZh_YQtGSLeSQmzqBYQ\"},\"__uuid\":\"U2Ia_IoVSXqfqFK99RzP6A\"},\"__uuid\":\"MeCEROwwRCuV-ubMbswzLQ\"}],\"__uuid\":\"mECya6MUTpK3hfcHhNulTw\"},\"__uuid\":\"ZaoOIoquT2WW4s3kbfNOcg\"},\"__uuid\":\"ZSj5psVHS8GtOHnJXTIqow\"},\"__uuid\":\"nXDrQ1P7QNq8usQfTujHag\"},{\"stage\":{\"name\":\"Pipeline Rollback\",\"identifier\":\"prb-CPpY3OUOTMCL3Dik_Mae1A\",\"type\":\"PipelineRollback\",\"spec\":{\"__uuid\":\"slRRDEQiQXWnf899LsZYBw\"},\"__uuid\":\"cQI4coYKTD2knzdvBdPbIw\"},\"__uuid\":\"URwbwN0gTJaSu2IVOaxLPg\"}],\"allowStageExecutions\":true,\"properties\":{\"ci\":{\"codebase\":{\"connectorRef\":\"GitConnector\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"main\",\"__uuid\":\"44gBvsDCSgqaUPHz1dTbmw\"},\"__uuid\":\"yd0aGNA7Tz66wI39VYw9nw\"},\"__uuid\":\"WePlvp-UQ-qKWLOY87QfVw\"},\"__uuid\":\"imfpchlYT9iu0Vlvz7Gpxg\"},\"__uuid\":\"N5X-0uN7S2W0dIZqt-HtJA\"},\"__uuid\":\"qEx8D7bLTgOd5wi8v8JISw\"},\"__uuid\":\"lW4Iw_FBR_2WT1I8d267_A\"}";

    ObjectMapper mapper = new ObjectMapper();
    String pipelineYaml =
        "{\"identifier\":\"build2\",\"type\":\"CI\",\"name\":\"build2\",\"description\":\"\",\"spec\":{\"cloneCodebase\":false,\"infrastructure\":{\"useFromStage\":\"build\",\"__uuid\":\"ZUlshyaWQLiDeBWcLBW1cQ\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"0LRwUUsHT3C82eqz60BPKw\"},\"__uuid\":\"_93bWUtqRf2eKlbhWDKHEw\"},\"__uuid\":\"UQH5KkVARxCCqJU-lXoXeQ\"}],\"__uuid\":\"jVZnywtxRvSh4yWZ4o_gXA\"},\"__uuid\":\"MqgG3Ic5Ti6U5RzqHeUnlA\"},\"__uuid\":\"7UdLGSeHSu-ScyLJUgkmgw\"}";
    JsonNode rootJsonNode = mapper.readTree(pipelineYaml);
    YamlNode parentYamlNode = new YamlNode("name", rootJsonNode);
    YamlNode rootYamlNode = new YamlNode("name1", rootJsonNode, parentYamlNode);

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(new YamlField(rootYamlNode)).yaml(yaml).build();

    String stageConfigYaml =
        "{\"identifier\":\"build\",\"type\":\"CI\",\"name\":\"build\",\"spec\":{\"cloneCodebase\":true,\"caching\":{\"enabled\":false,\"__uuid\":\"RsxfjJf-QLynxcgn2XcuDw\"},\"infrastructure\":{\"type\":\"KubernetesDirect\",\"spec\":{\"connectorRef\":\"account.SahithiK8\",\"namespace\":\"harness-delegate-ng\",\"automountServiceAccountToken\":true,\"nodeSelector\":{\"__uuid\":\"X3z2nr2UTTCcM2Mg0dx2Mw\"},\"os\":\"Linux\",\"__uuid\":\"Yd7jqM6_TIK2LotPX6eFsA\"},\"__uuid\":\"ILpY9LYFTTauhmtQpna32A\"},\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"Run_1\",\"type\":\"Run\",\"name\":\"Run_1\",\"spec\":{\"connectorRef\":\"SahithiDockerConnector\",\"image\":\"alpine\",\"shell\":\"Sh\",\"command\":\"echo \\\"hello\\\"\",\"__uuid\":\"0axuv_e_SGmnfIJdiGElCQ\"},\"__uuid\":\"IyPmA2KjSzKBSMJbP8LRwg\"},\"__uuid\":\"kMFQ2U2PT-G7CfJ89ywLBQ\"}],\"__uuid\":\"pubuFmFGRlK8adcOtIvIFw\"},\"__uuid\":\"kenxSwWQQOejCSlD2GwkLA\"},\"__uuid\":\"lQhkxyhnRVSvLtRCDc71eA\"}";
    JsonNode stageConfigRootJsonNode = mapper.readTree(stageConfigYaml);
    YamlNode stageConfigParentYamlNode = new YamlNode("name", stageConfigRootJsonNode);
    YamlNode stageConfigRootYamlNode = new YamlNode("name1", stageConfigRootJsonNode, stageConfigParentYamlNode);

    when(PlanCreatorUtils.getStageConfig(any(), anyString())).thenReturn(new YamlField(stageConfigRootYamlNode));

    Infrastructure infrastructure = integrationStageStepParametersPMS.getInfrastructure(stageNode, ctx);
    assertThat(infrastructure.getType()).isEqualTo(Infrastructure.Type.KUBERNETES_DIRECT);
  }
}
