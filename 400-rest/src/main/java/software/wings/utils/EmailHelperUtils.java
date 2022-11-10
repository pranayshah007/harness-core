/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class EmailHelperUtils {
  @Inject private SettingsService settingsService;
  public static final String NG_SMTP_SETTINGS_PREFIX = "ngSmtpConfig-";

  public SmtpConfig getSmtpConfig(String accountId, Boolean isNg) {
    if (accountId == null) {
      return null;
    }
    List<SettingAttribute> smtpAttribute =
        settingsService.getGlobalSettingAttributesByType(accountId, SettingVariableTypes.SMTP.name());
    for (SettingAttribute settingAttribute : smtpAttribute) {
      String smtpAttributeName = settingAttribute.getName();
      SmtpConfig config = (SmtpConfig) settingAttribute.getValue();
      if (isSmtpConfigValid(config) && smtpAttributeName != null) {
        boolean isNgSmtp = isNgSmtp(smtpAttributeName);
        if ((isNg && isNgSmtp) || (!isNg && !isNgSmtp)) {
          return config;
        }
      }
    }
    return null;
  }

  public boolean isNgSmtp(String smtpAttributeName) {
    return smtpAttributeName.length() >= 13
        && (NG_SMTP_SETTINGS_PREFIX.equalsIgnoreCase(smtpAttributeName.substring(0, 13)));
  }

  public boolean isSmtpConfigValid(SmtpConfig config) {
    return config != null && config.valid();
  }
}
