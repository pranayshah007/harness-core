package software.wings.service.impl.yaml.handler.setting.artifactserver;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.BambooConfig;
import software.wings.beans.BambooConfigYaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;

import com.google.inject.Singleton;
import java.util.List;

/**
 * @author rktummala on 11/19/17
 */
@OwnedBy(CDC)
@Singleton
public class BambooConfigYamlHandler extends ArtifactServerYamlHandler<BambooConfigYaml, BambooConfig> {
  @Override
  public BambooConfigYaml toYaml(SettingAttribute settingAttribute, String appId) {
    BambooConfig bambooConfig = (BambooConfig) settingAttribute.getValue();
    BambooConfigYaml yaml =
        BambooConfigYaml.builder()
            .harnessApiVersion(getHarnessApiVersion())
            .type(bambooConfig.getType())
            .url(bambooConfig.getBambooUrl())
            .username(bambooConfig.getUsername())
            .password(getEncryptedYamlRef(bambooConfig.getAccountId(), bambooConfig.getEncryptedPassword()))
            .build();
    toYaml(yaml, settingAttribute, appId);
    return yaml;
  }

  @Override
  protected SettingAttribute toBean(
      SettingAttribute previous, ChangeContext<BambooConfigYaml> changeContext, List<ChangeContext> changeSetContext) {
    String uuid = previous != null ? previous.getUuid() : null;
    BambooConfigYaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    BambooConfig config = BambooConfig.builder()
                              .accountId(accountId)
                              .bambooUrl(yaml.getUrl())
                              .encryptedPassword(yaml.getPassword())
                              .username(yaml.getUsername())
                              .build();
    return buildSettingAttribute(accountId, changeContext.getChange().getFilePath(), uuid, config);
  }

  @Override
  public Class getYamlClass() {
    return BambooConfigYaml.class;
  }
}
