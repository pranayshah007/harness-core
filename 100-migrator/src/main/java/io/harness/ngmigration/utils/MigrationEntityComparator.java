/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.secrets.SecretFactory;

import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.collect.ImmutableMap;
import java.util.Comparator;
import java.util.Map;

public class MigrationEntityComparator implements Comparator<NGYamlFile> {
  private static final int APPLICATION = 0;
  private static final int SECRET_MANAGER_TEMPLATE = 1;
  private static final int SECRET_MANAGER = 2;
  private static final int SECRET = 5;
  private static final int TEMPLATE = 7;
  private static final int SERVICE_COMMAND_TEMPLATE = 8;
  private static final int CONNECTOR = 10;
  private static final int CONTAINER_TASK = 13;
  private static final int ECS_SERVICE_SPEC = 14;
  private static final int MANIFEST = 15;
  private static final int CONFIG_FILE = 16;
  private static final int AMI_STARTUP_SCRIPT = 17;
  private static final int ELASTIGROUP_CONFIGURATION = 18;
  private static final int SERVICE = 20;
  private static final int INFRA_PROVISIONER = 23;
  private static final int ENVIRONMENT = 25;
  private static final int INFRA = 35;
  private static final int SERVICE_VARIABLE = 40;
  private static final int MONITORED_SERVICE_TEMPLATE = 65;
  private static final int WORKFLOW = 70;
  private static final int PIPELINE = 100;

  private static final int TRIGGER = 150;
  private static final int USER_GROUP = -2;
  private static final int FILE_STORE = -1;

  public static final Map<NGMigrationEntityType, Integer> MIGRATION_ORDER =
      ImmutableMap.<NGMigrationEntityType, Integer>builder()
          .put(NGMigrationEntityType.USER_GROUP, USER_GROUP)
          .put(NGMigrationEntityType.FILE_STORE, FILE_STORE)
          .put(NGMigrationEntityType.APPLICATION, APPLICATION)
          .put(NGMigrationEntityType.SECRET_MANAGER_TEMPLATE, SECRET_MANAGER_TEMPLATE)
          .put(NGMigrationEntityType.SECRET_MANAGER, SECRET_MANAGER)
          .put(NGMigrationEntityType.TEMPLATE, TEMPLATE)
          .put(NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE, SERVICE_COMMAND_TEMPLATE)
          .put(NGMigrationEntityType.CONNECTOR, CONNECTOR)
          .put(NGMigrationEntityType.CONTAINER_TASK, CONTAINER_TASK)
          .put(NGMigrationEntityType.ECS_SERVICE_SPEC, ECS_SERVICE_SPEC)
          .put(NGMigrationEntityType.AMI_STARTUP_SCRIPT, AMI_STARTUP_SCRIPT)
          .put(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION, ELASTIGROUP_CONFIGURATION)
          .put(NGMigrationEntityType.MANIFEST, MANIFEST)
          .put(NGMigrationEntityType.CONFIG_FILE, CONFIG_FILE)
          .put(NGMigrationEntityType.SERVICE, SERVICE)
          .put(NGMigrationEntityType.INFRA_PROVISIONER, INFRA_PROVISIONER)
          .put(NGMigrationEntityType.ENVIRONMENT, ENVIRONMENT)
          .put(NGMigrationEntityType.INFRA, INFRA)
          .put(NGMigrationEntityType.SERVICE_VARIABLE, SERVICE_VARIABLE)
          .put(NGMigrationEntityType.WORKFLOW, WORKFLOW)
          .put(NGMigrationEntityType.PIPELINE, PIPELINE)
          .put(NGMigrationEntityType.TRIGGER, TRIGGER)
          .put(NGMigrationEntityType.MONITORED_SERVICE_TEMPLATE, MONITORED_SERVICE_TEMPLATE)
          .build();

  @Override
  public int compare(NGYamlFile o1, NGYamlFile o2) {
    int o1Int = toInt(o1);
    int o2Int = toInt(o2);

    if (o1Int == o2Int && o1.getType().equals(NGMigrationEntityType.FILE_STORE)
        && o2.getType().equals(NGMigrationEntityType.FILE_STORE)) {
      FileYamlDTO f1 = (FileYamlDTO) o1.getYaml();
      FileYamlDTO f2 = (FileYamlDTO) o2.getYaml();
      return Integer.compare(f1.getDepth(), f2.getDepth());
    }

    return Integer.compare(o1Int, o2Int);
  }

  private static int toInt(NGYamlFile file) {
    if (NGMigrationEntityType.SECRET == file.getType()) {
      return SecretFactory.isStoredInHarnessSecretManager(file) ? Integer.MIN_VALUE : SECRET;
    }
    if (MIGRATION_ORDER.containsKey(file.getType())) {
      return MIGRATION_ORDER.get(file.getType());
    }
    throw new InvalidArgumentsException("Unknown type found: " + file.getType());
  }
}
