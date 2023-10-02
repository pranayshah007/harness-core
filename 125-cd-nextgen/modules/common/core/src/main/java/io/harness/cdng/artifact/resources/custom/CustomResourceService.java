/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.custom;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public interface CustomResourceService {
  List<BuildDetails> getBuilds(String script, String versionPath, String arrayPath, Map<String, String> inputs,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, int secretFunctor,
      List<TaskSelectorYaml> delegateSelector);
}
