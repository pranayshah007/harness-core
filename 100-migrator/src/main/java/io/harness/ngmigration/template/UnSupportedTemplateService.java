/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import software.wings.beans.template.Template;

import com.fasterxml.jackson.databind.JsonNode;

public class UnSupportedTemplateService implements NgTemplateService {
  public boolean isMigrationSupported() {
    return false;
  }
  @Override
  public JsonNode getNgTemplateConfigSpec(Template template, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return null;
  }

  @Override
  public String getTimeoutString(Template template) {
    return null;
  }
}
