/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.ngmigration.NGMigrationStatus;

@OwnedBy(HarnessTeam.CDC)
public interface NgTemplateService {

  static boolean isMigrationSupported(Template template) {
    return template.getTemplateObject() instanceof HttpTemplate
            || template.getTemplateObject() instanceof ShellScriptTemplate;
  }
  YamlDTO getNgTemplateConfig(Template template, String orgIdentifier, String projectIdentifier);
}
