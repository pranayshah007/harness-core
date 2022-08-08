/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static freemarker.template.Configuration.VERSION_2_3_23;

import io.harness.exception.ngexception.NGTemplateException;
import io.harness.utils.LazyInitHelper;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public class SshInitCommandTemplates {
  public static final String EXECLAUNCHERV2_SH_FTL = "execlauncherv2.sh.ftl";
  public static final String TAILWRAPPERV2_SH_FTL = "tailwrapperv2.sh.ftl";

  private static final Configuration cfgV2 = new Configuration(VERSION_2_3_23);

  private static volatile boolean initV2;

  public static Template getTemplate(String templateName) {
    LazyInitHelper.apply(
        SshInitCommandTemplates.class, () -> !SshInitCommandTemplates.initV2, SshInitCommandTemplates::initCfgV2);
    return getTemplateFromConfig(templateName);
  }

  private static Template getTemplateFromConfig(String templateName) {
    try {
      return cfgV2.getTemplate(templateName);
    } catch (IOException e) {
      throw new NGTemplateException(e.getMessage(), e);
    }
  }

  private static void putTemplateToConfig(String templateName, StringTemplateLoader templateLoader) {
    InputStream inputStream =
        SshInitCommandTemplates.class.getClassLoader().getResourceAsStream("commandtemplates/" + templateName);
    if (inputStream == null) {
      throw new NGTemplateException(templateName + " is missing.");
    }

    try {
      templateLoader.putTemplate(
          templateName, convertToUnixStyleLineEndings(IOUtils.toString(inputStream, StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new NGTemplateException(e.getMessage(), e);
    }
  }

  private static void initCfgV2() {
    StringTemplateLoader templateLoader = new StringTemplateLoader();
    putTemplateToConfig(EXECLAUNCHERV2_SH_FTL, templateLoader);
    putTemplateToConfig(TAILWRAPPERV2_SH_FTL, templateLoader);
    cfgV2.setTemplateLoader(templateLoader);
    initV2 = true;
  }

  private static String convertToUnixStyleLineEndings(String input) {
    return input.replace("\r\n", "\n");
  }
}
