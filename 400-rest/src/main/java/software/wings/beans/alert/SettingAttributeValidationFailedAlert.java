/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.alert;

import static software.wings.beans.SettingAttribute.SettingCategory;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.alert.AlertData;

import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
public class SettingAttributeValidationFailedAlert implements AlertData {
  @Inject @Transient @SchemaIgnore private SettingsService settingsService;

  private String settingId;
  private String settingCategory;
  private String connectivityError;

  @Override
  public boolean matches(AlertData alertData) {
    SettingAttributeValidationFailedAlert otherAlert = (SettingAttributeValidationFailedAlert) alertData;
    return StringUtils.equals(otherAlert.getSettingId(), settingId);
  }

  @Override
  public String buildTitle() {
    StringBuilder title = new StringBuilder(128);
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      title.append("Connector ");
    } else {
      if (settingAttribute.getCategory() == SettingCategory.CLOUD_PROVIDER) {
        title.append("Cloud provider ");
      } else {
        title.append("Connector ");
      }

      title.append('[').append(settingAttribute.getName()).append("] ");
    }

    if (isBlank(connectivityError)) {
      title.append("has invalid/expired credentials");
    } else {
      title.append("has a connectivity error: ").append(connectivityError);
    }

    return title.toString();
  }
}
