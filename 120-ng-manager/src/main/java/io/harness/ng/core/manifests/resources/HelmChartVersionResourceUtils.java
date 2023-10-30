/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class HelmChartVersionResourceUtils {
  private final ArtifactResourceUtils artifactResourceUtils;
  String resolveExpression(String accountId, String orgId, String projectId, String pipelineId, String runtimeInputYaml,
      String fieldValue, String finalFieldValue, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo,
      String serviceRef) {
    return artifactResourceUtils
        .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgId, projectId, pipelineId, runtimeInputYaml,
            isEmpty(fieldValue) ? finalFieldValue : fieldValue, fqnPath, gitEntityBasicInfo, serviceRef, null)
        .getValue();
  }
}
