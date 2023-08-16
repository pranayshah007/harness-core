/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_APP_PATH;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_COMMAND_OPTIONS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(CDP)
@Singleton
public class AwsCdkHelper {
  public static final String GIT_COMMIT_ID = "GIT_COMMIT_ID";
  public static final String LATEST_SUCCESSFUL_PROVISIONING_COMMIT_ID = "LATEST_SUCCESSFUL_PROVISIONING_COMMIT_ID";
  public static final List<String> OUTPUT_KEYS = Arrays.asList(GIT_COMMIT_ID, "CDK_OUTPUT");
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Inject private PipelineRbacHelper pipelineRbacHelper;

  public void validateFeatureEnabled(Ambiance ambiance) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_AWS_CDK)) {
      throw new AccessDeniedException("AWS CDK is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  public void validateRuntimePermissions(Ambiance ambiance, AwsCdkBaseStepInfo awsCdkBaseStepInfo) {
    List<EntityDetail> entityDetailList = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    String connectorRef = awsCdkBaseStepInfo.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  public HashMap<String, String> getCommonEnvVariables(
      String appPath, List<String> commandOptions, ParameterField<Map<String, String>> envVariables) {
    HashMap<String, String> environmentVariablesMap = new HashMap<>();
    environmentVariablesMap.put(PLUGIN_AWS_CDK_APP_PATH, appPath);

    if (isNotEmpty(commandOptions)) {
      environmentVariablesMap.put(PLUGIN_AWS_CDK_COMMAND_OPTIONS, String.join(" ", commandOptions));
    }
    if (envVariables != null && envVariables.getValue() != null) {
      environmentVariablesMap.putAll(envVariables.getValue());
    }

    return environmentVariablesMap;
  }

  public Map<String, String> processOutput(StepMapOutput stepOutput) {
    Map<String, String> processedOutput = new HashMap<>();
    stepOutput.getMap().forEach((key, value) -> {
      if (OUTPUT_KEYS.contains(key)) {
        processedOutput.put(key, new String(Base64.getDecoder().decode(value.replace("-", "="))));
      } else {
        processedOutput.put(key, value);
      }
    });
    return processedOutput;
  }
}
