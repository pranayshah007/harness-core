/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.Builder;

public class CreatePRStepParams extends CreatePRBaseStepInfo implements GitOpsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public CreatePRStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, ParameterField<StoreConfigWrapper> store,
      ParameterField<String> commitMessage, ParameterField<String> targetBranch, ParameterField<Boolean> isNewBranch,
      ParameterField<String> prTitle) {
    super(delegateSelectors, stringMap, store, commitMessage, targetBranch, isNewBranch, prTitle);
  }
}
