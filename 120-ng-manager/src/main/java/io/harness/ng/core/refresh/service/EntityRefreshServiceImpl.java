/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.service;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.NgAutoLogContextForMethod;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.refresh.helper.CDInputsValidationHelper;
import io.harness.ng.core.refresh.helper.RefreshInputsHelper;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EntityRefreshServiceImpl implements EntityRefreshService {
  @Inject CDInputsValidationHelper CDInputsValidationHelper;
  @Inject RefreshInputsHelper refreshInputsHelper;

  @Override
  public InputsValidationResponse validateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml, String resolvedTemplatesYaml) {
    long start = System.currentTimeMillis();
    try (AutoLogContext ignore1 =
             new NgAutoLogContextForMethod(projectId, orgId, accountId, "validateInputsForYaml", OVERRIDE_NESTS);) {
      log.info("[NGManager] Starting validateInputsForYaml to yaml");
      return CDInputsValidationHelper.validateInputsForYaml(accountId, orgId, projectId, yaml, resolvedTemplatesYaml);
    } finally {
      log.info("[NGManager] validateInputsForYaml took {}ms ", System.currentTimeMillis() - start);
    }
  }

  @Override
  public String refreshLinkedInputs(
      String accountId, String orgId, String projectId, String yaml, String resolvedTemplatesYaml) {
    return refreshInputsHelper.refreshInputs(accountId, orgId, projectId, yaml, resolvedTemplatesYaml);
  }
}
