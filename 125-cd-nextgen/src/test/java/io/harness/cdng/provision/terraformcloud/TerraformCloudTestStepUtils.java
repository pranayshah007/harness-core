/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudRefreshSpecParameters;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;

import java.util.List;

@OwnedBy(CDP)
public class TerraformCloudTestStepUtils {
  public static final String TFC_SWEEPING_OUTPUT_IDENTIFIER =
      "tfcPlanOutput_Plan_test-account/test-org/test-project/provisionerId";
  public static final ParameterField<String> CONNECTOR_REF = ParameterField.createValueField("tcConnectorRef");
  public static final ParameterField<String> PROVISIONER_ID = ParameterField.createValueField("provisionerId");
  public static final ParameterField<String> ORG = ParameterField.createValueField("org");
  public static final ParameterField<String> WS = ParameterField.createValueField("ws");
  public static final ParameterField<Boolean> EXPORT_TF_JSON = ParameterField.createValueField(true);
  public static final ParameterField<String> TF_VERSION = ParameterField.createValueField("123");

  public Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .setPlanExecutionId("planExecutionId")
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  public TerraformCloudPlanSpecParameters getPlanSpecParameters() {
    return TerraformCloudPlanSpecParameters.builder()
        .exportTerraformPlanJson(ParameterField.<Boolean>builder().value(true).build())
        .planType(PlanType.APPLY)
        .connectorRef(CONNECTOR_REF)
        .provisionerIdentifier(PROVISIONER_ID)
        .organization(ORG)
        .workspace(WS)
        .exportTerraformPlanJson(EXPORT_TF_JSON)
        .build();
  }

  public TerraformCloudApplySpecParameters getApplySpecParameters() {
    return TerraformCloudApplySpecParameters.builder().provisionerIdentifier(PROVISIONER_ID).build();
  }

  public TerraformCloudRunStepParameters getTerraformCloudRunStepParams(TerraformCloudRunType type) {
    TerraformCloudRunSpecParameters terraformCloudRunSpecParameters = null;
    switch (type) {
      case REFRESH_STATE:
        terraformCloudRunSpecParameters = createRefreshSpecParams();
        break;
      case PLAN_ONLY:
        terraformCloudRunSpecParameters = createPlanOnly();
        break;
      case PLAN_AND_APPLY:
        terraformCloudRunSpecParameters = createPlanAndApply();
        break;
      case PLAN_AND_DESTROY:
        terraformCloudRunSpecParameters = createPlanAndDestroy();
        break;
      case PLAN:
        terraformCloudRunSpecParameters = getPlanSpecParameters();
        break;
      case APPLY:
        terraformCloudRunSpecParameters = getApplySpecParameters();
        break;
      default:
        throw new InvalidRequestException("Unsupported type");
    }
    return TerraformCloudRunStepParameters.infoBuilder()
        .message(ParameterField.createValueField("Triggered from Harness"))
        .spec(terraformCloudRunSpecParameters)
        .build();
  }

  public TerraformCloudPlanAndDestroySpecParameters createPlanAndDestroy() {
    return TerraformCloudPlanAndDestroySpecParameters.builder()
        .connectorRef(CONNECTOR_REF)
        .provisionerIdentifier(PROVISIONER_ID)
        .organization(ORG)
        .workspace(WS)
        .build();
  }

  public TerraformCloudPlanAndApplySpecParameters createPlanAndApply() {
    return TerraformCloudPlanAndApplySpecParameters.builder()
        .connectorRef(CONNECTOR_REF)
        .provisionerIdentifier(PROVISIONER_ID)
        .organization(ORG)
        .workspace(WS)
        .build();
  }

  public TerraformCloudPlanOnlySpecParameters createPlanOnly() {
    return TerraformCloudPlanOnlySpecParameters.builder()
        .connectorRef(CONNECTOR_REF)
        .organization(ORG)
        .workspace(WS)
        .planType(PlanType.APPLY)
        .provisionerIdentifier(PROVISIONER_ID)
        .exportTerraformPlanJson(EXPORT_TF_JSON)
        .terraformVersion(TF_VERSION)
        .build();
  }

  public TerraformCloudRefreshSpecParameters createRefreshSpecParams() {
    return TerraformCloudRefreshSpecParameters.builder()
        .connectorRef(CONNECTOR_REF)
        .organization(ORG)
        .workspace(WS)
        .build();
  }

  public TerraformCloudTaskParams getTerraformCloudTaskParams(TerraformCloudTaskType type) {
    return TerraformCloudTaskParams.builder()
        .terraformCloudTaskType(type)
        .terraformVersion("123")
        .runId("run-123")
        .planType(io.harness.delegate.beans.terraformcloud.PlanType.APPLY)
        .targets(List.of("t1", "t2", "t3"))
        .exportJsonTfPlan(true)
        .discardPendingRuns(true)
        .workspace("ws")
        .organization("org")
        .build();
  }

  public TerraformCloudConnectorDTO getTerraformCloudConnector() {
    return TerraformCloudConnectorDTO.builder()
        .terraformCloudUrl("https://some.io")
        .credential(TerraformCloudCredentialDTO.builder()
                        .type(TerraformCloudCredentialType.API_TOKEN)
                        .spec(TerraformCloudTokenCredentialsDTO.builder()
                                  .apiToken(SecretRefData.builder()
                                                .identifier("tokenRefIdentifier")
                                                .decryptedValue("t-o-k-e-n".toCharArray())
                                                .scope(Scope.ACCOUNT)
                                                .build())
                                  .build())
                        .build())
        .build();
  }
}
