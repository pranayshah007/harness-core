package software.wings.beans.dto;

import io.harness.beans.EmbeddedUser;;
import io.harness.yaml.BaseYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Transient;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.validation.ConnectivityValidationAttributes;

import javax.validation.Valid;
import java.util.List;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

@Data
@SuperBuilder
public class SettingAttribute {
    private String envId = GLOBAL_ENV_ID;
    private String accountId;
    private String name;
    private SettingValue value;
    private ConnectivityValidationAttributes connectivityValidationAttributes;
    private software.wings.beans.SettingAttribute.SettingCategory category = software.wings.beans.SettingAttribute.SettingCategory.SETTING;
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
