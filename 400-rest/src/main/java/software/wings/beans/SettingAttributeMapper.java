package software.wings.beans;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SettingAttributeMapper {
  public SettingAttribute fromSettingAttributeDTO(software.wings.beans.dto.SettingAttribute settingAttribute) {
    return SettingAttribute.Builder.aSettingAttribute()
        .withAccountId(settingAttribute.getAccountId())
        .withAppIds(settingAttribute.getAppIds())
        .withConnectivityValidationAttributes(settingAttribute.getConnectivityValidationAttributes())
        .withConnectivityError(settingAttribute.getConnectivityError())
        .withName(settingAttribute.getName())
        .withUsageRestrictions(settingAttribute.getUsageRestrictions())
        .withValue(settingAttribute.getValue())
        .withEnvId(settingAttribute.getEnvId())
        .withSample(settingAttribute.isSample())
        .build();
  }

  public software.wings.beans.dto.SettingAttribute toSettingAttributeDTO(SettingAttribute settingAttribute) {
    return software.wings.beans.dto.SettingAttribute.builder()
        .accountId(settingAttribute.getAccountId())
        .appId(settingAttribute.getAppId())
        .connectivityError(settingAttribute.getConnectivityError())
        .connectivityValidationAttributes(settingAttribute.getValidationAttributes())
        .envId(settingAttribute.getEnvId())
        .value(settingAttribute.getValue())
        .name(settingAttribute.getName())
        .sample(settingAttribute.isSample())
        .build();
  }
}
