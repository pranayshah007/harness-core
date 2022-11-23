/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.configAsCode;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;

import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.testframework.framework.Setup;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.Builder;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.inject.Inject;
import io.restassured.http.ContentType;

public class ConfigAsCodeHelper {
  @Inject private OwnerManager ownerManager;
  @Inject ApplicationGenerator applicationGenerator;
  @Inject EnvironmentGenerator environmentGenerator;

  public void createYamlGitConfig(String accountId, String appId, String connectorId, String bearerToken) {
    YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                      .accountId(accountId)
                                      .entityId(appId)
                                      .syncMode(SyncMode.BOTH)
                                      .branchName("master")
                                      .enabled(true)
                                      .gitConnectorId(connectorId)
                                      .entityType(EntityType.APPLICATION)
                                      .build();
    yamlGitConfig.setAppId(appId);

    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("accountId", accountId)
        .body(yamlGitConfig)
        .contentType(ContentType.JSON)
        .post("/setup-as-code/yaml/git-config");
  }

  public Application createAppWithYaml(String appName, String connectorID, String webhookToken, String accountID) {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                      .entityType(EntityType.APPLICATION)
                                      .branchName("master")
                                      .enabled(true)
                                      .gitConnectorId(connectorID)
                                      .syncMode(SyncMode.BOTH)
                                      .webhookToken(webhookToken)
                                      .accountId(accountID)
                                      .build();

    final Application app = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name(appName).yamlGitConfig(yamlGitConfig).build());

    return app;
  }

  public Application createApp(String appName) {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    final Application app = applicationGenerator.ensureApplication(seed, owners, anApplication().name(appName).build());

    return app;
  }

  public Environment createEnvironment(Application app, String envName) {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    final Builder builder = anEnvironment().appId(app.getUuid());
    final Environment env = environmentGenerator.ensureEnvironment(seed, owners, builder.name(envName).build());

    return env;
  }
}
