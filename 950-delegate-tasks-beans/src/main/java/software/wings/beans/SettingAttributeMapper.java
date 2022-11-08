package software.wings.beans;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SettingAttributeMapper {

    public SettingAttribute fromSettingAttributeDTO(software.wings.beans.dto.SettingAttribute settingAttribute){
        return SettingAttribute.Builder.aSettingAttribute().build();
    }

    public software.wings.beans.dto.SettingAttribute toSettingAttributeDTO(SettingAttribute settingAttribute){
        return software.wings.beans.dto.SettingAttribute.builder().build();
    }



}
