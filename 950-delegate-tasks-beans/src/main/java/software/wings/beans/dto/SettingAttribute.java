package software.wings.beans.dto;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

import io.harness.beans.EmbeddedUser;
;

import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.validation.ConnectivityValidationAttributes;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class SettingAttribute {
  private String envId = GLOBAL_ENV_ID;
  private String accountId;
  private String name;
  private SettingValue value;
  private ConnectivityValidationAttributes connectivityValidationAttributes;
  private software.wings.beans.dto.SettingCategory category = software.wings.beans.dto.SettingCategory.SETTING;
  private List<String> appIds;
  private String uuid;
  private String appId = GLOBAL_APP_ID;
  private EmbeddedUser createdBy;
  private long createdAt;
  private EmbeddedUser lastUpdatedBy;
  private long lastUpdatedAt;
  private UsageRestrictions usageRestrictions;
  private boolean sample;
  private String connectivityError;
  private ConnectivityValidationAttributes validationAttributes;
}
