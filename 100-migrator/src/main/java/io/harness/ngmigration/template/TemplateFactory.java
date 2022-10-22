/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;

@OwnedBy(HarnessTeam.CDC)
public class TemplateFactory {
  private static final HttpTemplateService httpTemplateService = new HttpTemplateService();

  private static final ShellScriptTemplateService shellScriptTemplateService = new ShellScriptTemplateService();

  private static final UnSupportedTemplateService unSupportedTemplateService = new UnSupportedTemplateService();
  public static NgTemplateService getTemplateService(Template template) {
    if (TemplateType.SHELL_SCRIPT.name().equals(template.getType())) {
      return shellScriptTemplateService;
    } else if (TemplateType.HTTP.name().equals(template.getType())) {
      return httpTemplateService;
    }
    return unSupportedTemplateService;
  }
}
