/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class TemplateFactory {
  private static final HttpTemplateService httpTemplateService = new HttpTemplateService();

  private static final ShellScriptTemplateService shellScriptTemplateService = new ShellScriptTemplateService();
  private static final CustomDeploymentTemplateService customDeploymentTemplateService =
      new CustomDeploymentTemplateService();
  private static final CustomArtifactSourceTemplateService customArtifactSourceTemplateService =
      new CustomArtifactSourceTemplateService();
  private static final ServiceCommandTemplateService serviceCommandTemplateService =
      new ServiceCommandTemplateService();

  private static final UnSupportedTemplateService unSupportedTemplateService = new UnSupportedTemplateService();
  public static NgTemplateService getTemplateService(Template template) {
    if (TemplateType.SHELL_SCRIPT.name().equals(template.getType())) {
      return shellScriptTemplateService;
    } else if (TemplateType.HTTP.name().equals(template.getType())) {
      return httpTemplateService;
    } else if (TemplateType.SSH.name().equals(template.getType())) {
      return serviceCommandTemplateService;
    } else if (TemplateType.CUSTOM_DEPLOYMENT_TYPE.name().equals(template.getType())) {
      return customDeploymentTemplateService;
    } else if (TemplateType.ARTIFACT_SOURCE.name().equals(template.getType())) {
      return customArtifactSourceTemplateService;
    }
    return unSupportedTemplateService;
  }
}
