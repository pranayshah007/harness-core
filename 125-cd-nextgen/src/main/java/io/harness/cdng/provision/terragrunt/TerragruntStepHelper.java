/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class TerragruntStepHelper {
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  public static StepType addStepType(String yamlType) {
    return StepType.newBuilder().setType(yamlType).setStepCategory(StepCategory.STEP).build();
  }

  public void checkIfTerragruntFeatureIsEnabled(Ambiance ambiance, String step) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.TERRAGRUNT_PROVISION_NG)) {
      throw new AccessDeniedException(
          format("'%s' is not enabled for account '%s'. Please contact harness customer care to enable FF '%s'.", step,
              AmbianceUtils.getAccountId(ambiance), FeatureName.TERRAGRUNT_PROVISION_NG.name()),
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  public static void addConnectorRef(
      Map<String, ParameterField<String>> connectorRefMap, TerragruntStepConfiguration terragruntStepConfiguration) {
    if (terragruntStepConfiguration.terragruntStepConfigurationType == TerragruntStepConfigurationType.INLINE) {
      TerragruntExecutionData terragruntExecutionData = terragruntStepConfiguration.terragruntExecutionData;

      connectorRefMap.put("configuration.spec.configFiles.store.spec.connectorRef",
          terragruntExecutionData.getTerragruntConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerragruntVarFileWrapper> terragruntVarFiles = terragruntExecutionData.getTerragruntVarFiles();
      addConnectorRefFromVarFiles(terragruntVarFiles, connectorRefMap);

      TerragruntBackendConfig terragruntBackendConfig = terragruntExecutionData.getTerragruntBackendConfig();
      addConnectorRefFromBackendConfig(terragruntBackendConfig, connectorRefMap);
    }
  }

  public static void addConnectorRefFromVarFiles(
      List<TerragruntVarFileWrapper> terragruntVarFiles, Map<String, ParameterField<String>> connectorRefMap) {
    if (EmptyPredicate.isNotEmpty(terragruntVarFiles)) {
      for (TerragruntVarFileWrapper terragruntVarFile : terragruntVarFiles) {
        if (terragruntVarFile.getVarFile().getType().equals(TerragruntVarFileTypes.Remote)) {
          connectorRefMap.put(
              "configuration.varFiles." + terragruntVarFile.getVarFile().identifier + ".spec.store.spec.connectorRef",
              ((RemoteTerragruntVarFileSpec) terragruntVarFile.varFile.spec).store.getSpec().getConnectorReference());
        }
      }
    }
  }

  public static void addConnectorRefFromBackendConfig(
      TerragruntBackendConfig terragruntBackendConfig, Map<String, ParameterField<String>> connectorRefMap) {
    if (terragruntBackendConfig != null
        && terragruntBackendConfig.getTerragruntBackendConfigSpec().getType().equals(
            TerragruntBackendFileTypes.Remote)) {
      connectorRefMap.put("configuration.spec.backendConfig.spec.store.spec.connectorRef",
          ((RemoteTerragruntBackendConfigSpec) terragruntBackendConfig.getTerragruntBackendConfigSpec())
              .store.getSpec()
              .getConnectorReference());
    }
  }
}
