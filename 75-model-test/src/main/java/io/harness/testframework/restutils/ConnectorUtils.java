package io.harness.testframework.restutils;

import static org.junit.Assert.assertNotNull;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.utils.UsageRestrictionsUtils.getAllAppAllEnvUsageRestrictions;

import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.restassured.path.json.JsonPath;
import software.wings.beans.DockerConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.settings.UsageRestrictions;

public class ConnectorUtils {
  public static String USER_NAME = "admin";

  public static String createArtifactoryConnector(String bearerToken, String connectorName, String accountId) {
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withName(connectorName)
            .withCategory(CONNECTOR)
            .withAccountId(accountId)
            .withValue(
                ArtifactoryConfig.builder()
                    .accountId(accountId)
                    .artifactoryUrl("https://harness.jfrog.io/harness")
                    .username("admin")
                    .password(new ScmSecret().decryptToCharArray(new SecretName("artifactory_connector_password")))
                    .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, accountId, settingAttribute);
    assertNotNull(setAttrResponse);
    return setAttrResponse.getString("resource.uuid").trim();
  }

  public static String createNexus2Connector(String bearerToken, String connectorName, String accountId) {
    String NEXUS_URL = "https://nexus2.harness.io";
    String VERSION = "2.x";

    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(connectorName)
            .withAccountId(accountId)
            .withValue(NexusConfig.builder()
                           .nexusUrl(NEXUS_URL)
                           .version(VERSION)
                           .username(USER_NAME)
                           .password(new ScmSecret().decryptToCharArray(new SecretName("harness_nexus")))
                           .accountId(accountId)
                           .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, accountId, settingAttribute);
    assertNotNull(setAttrResponse);
    return setAttrResponse.getString("resource.uuid").trim();
  }

  public static String createNexus3Connector(String bearerToken, String connectorName, String accountId) {
    String NEXUS_URL = "https://nexus3.harness.io";
    String VERSION = "3.x";

    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(connectorName)
            .withAccountId(accountId)
            .withValue(NexusConfig.builder()
                           .nexusUrl(NEXUS_URL)
                           .version(VERSION)
                           .username(USER_NAME)
                           .password(new ScmSecret().decryptToCharArray(new SecretName("harness_nexus")))
                           .accountId(accountId)
                           .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, accountId, settingAttribute);
    assertNotNull(setAttrResponse);
    return setAttrResponse.getString("resource.uuid").trim();
  }

  public static String createDockerRegistryConnector(String bearerToken, String connectorName, String accountId) {
    String DOCKER_URL = "https://registry.hub.docker.com/v2/";
    String USER_NAME = "wingsplugins";
    UsageRestrictions defaultUsageRestrictions = getAllAppAllEnvUsageRestrictions();

    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(connectorName)
            .withAccountId(accountId)
            .withUsageRestrictions(defaultUsageRestrictions)
            .withValue(DockerConfig.builder()
                           .dockerRegistryUrl(DOCKER_URL)
                           .username(USER_NAME)
                           .password(new ScmSecret().decryptToCharArray(new SecretName("harness_docker")))
                           .accountId(accountId)
                           .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, accountId, settingAttribute);
    assertNotNull(setAttrResponse);
    return setAttrResponse.getString("resource.uuid").trim();
  }
}