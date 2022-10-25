/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ENVIRONMENTS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INFRA_MAPPING_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;

import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.git.model.ChangeType;
import io.harness.migrations.Migration;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitFileChange.Builder;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.yaml.YamlChangeSetService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RemoveServiceInfraFolder implements Migration {
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private YamlChangeSetService yamlChangeSetService;
  private final String accountId = "kmpySmUISimoRrJL6NL73w";
  private final String DEBUG_LINE = "SERVICE_INFRA_YAML:";

  @Override
  public void migrate() {
    final List<Application> applications = appService.getAppsByAccountId(accountId);
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    for (Application app : applications) {
      log.info(HarnessStringUtils.join(StringUtils.SPACE, DEBUG_LINE, "Starting migration for app", app.getUuid()));
      try {
        List<Environment> environments = environmentService.getEnvByApp(app.getUuid());

        gitFileChanges.clear();

        environments.forEach(environment -> {
          GitFileChange gitFileChange =
              generateGitFileChangeForInfraMappingDelete(accountId, app.getName(), environment.getName());
          gitFileChanges.add(gitFileChange);
          log.info(HarnessStringUtils.join(
              StringUtils.SPACE, DEBUG_LINE, "Adding to git file changeSet", gitFileChange.getFilePath()));
        });
        yamlChangeSetService.saveChangeSet(accountId, gitFileChanges, app);
      } catch (Exception ex) {
        log.error(HarnessStringUtils.join(
            StringUtils.SPACE, DEBUG_LINE, ExceptionUtils.getMessage(ex), "app", app.getUuid()));
      }
      log.info(HarnessStringUtils.join(StringUtils.SPACE, DEBUG_LINE, "Finished migration for app", app.getUuid()));
    }
  }

  private GitFileChange generateGitFileChangeForInfraMappingDelete(String accountId, String appName, String envName) {
    return Builder.aGitFileChange()
        .withAccountId(accountId)
        .withChangeType(ChangeType.DELETE)
        .withFilePath(HarnessStringUtils.join(
            "/", SETUP_FOLDER, APPLICATIONS_FOLDER, appName, ENVIRONMENTS_FOLDER, envName, INFRA_MAPPING_FOLDER))
        .build();
  }
}
