/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.settings.SettingVariableTypes;

/**
 * Created by rishi on 10/30/16.
 */
@OwnedBy(PL)
public enum NotificationChannelType {
  EMAIL("Email", SettingVariableTypes.SMTP),
  SLACK("Slack", SettingVariableTypes.SLACK),
  WEB("WEB", null);

  private final String displayName;
  private final SettingVariableTypes settingVariableTypes;

  NotificationChannelType(String displayName, SettingVariableTypes settingVariableTypes) {
    this.displayName = displayName;
    this.settingVariableTypes = settingVariableTypes;
  }

  public String getDisplayName() {
    return displayName;
  }

  public SettingVariableTypes getSettingVariableTypes() {
    return settingVariableTypes;
  }
}
