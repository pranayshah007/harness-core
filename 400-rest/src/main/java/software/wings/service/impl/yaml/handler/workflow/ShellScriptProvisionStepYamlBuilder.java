/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.sm.states.provision.ShellScriptProvisionState.ShellScriptProvisionStateKeys;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@OwnedBy(CDP)
public class ShellScriptProvisionStepYamlBuilder extends InfraProvisionStepYamlBuilder {
  @Override
  public void convertIdToNameForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, Map<String, Object> inputProperties) {
    if (ShellScriptProvisionStateKeys.variables.equals(name)) {
      convertPropertyIdsToNames(name, appId, objectValue);
    } else if (ShellScriptProvisionStateKeys.provisionerId.equals(name)) {
      String value = String.valueOf(objectValue);
      if (!(value.startsWith("${") && value.endsWith("}"))) {
        objectValue = convertProvisionerIdToName(appId, objectValue);
      }

      name = PROVISIONER_NAME;
    }

    outputProperties.put(name, objectValue);
  }

  @Override
  public void convertNameToIdForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, String accountId, Map<String, Object> inputProperties) {
    if (ShellScriptProvisionStateKeys.variables.equals(name)) {
      convertPropertyNamesToIds(name, accountId, objectValue);
    } else if (PROVISIONER_NAME.equals(name)) {
      String value = String.valueOf(objectValue);
      if (!(value.startsWith("${") && value.endsWith("}"))) {
        objectValue = convertProvisionerNameToId(appId, objectValue);
      }

      name = ShellScriptProvisionStateKeys.provisionerId;
    }

    outputProperties.put(name, objectValue);
  }
}
