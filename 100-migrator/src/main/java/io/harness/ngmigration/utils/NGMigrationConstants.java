/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;

import io.harness.pms.yaml.ParameterField;

public interface NGMigrationConstants {
  String DISCOVERY_IMAGE_PATH = "/tmp/viz-output/viz.png";
  String DEFAULT_ZIP_DIRECTORY = "/tmp/zip-output";
  String ZIP_FILE_PATH = "/yamls.zip";
  String VIZ_TEMP_DIR_PREFIX = "viz-output";
  String VIZ_FILE_NAME = "/viz.png";
  String PLEASE_FIX_ME = "__PLEASE_FIX_ME__";
  String RUNTIME_INPUT = "<+input>";
  ParameterField<String> RUNTIME_FIELD = ParameterField.createValueField(RUNTIME_INPUT);
  String SERVICE_COMMAND_TEMPLATE_SEPARATOR = "::";
  String UNKNOWN_SERVICE = "UNKNOWN_S";
  String SECRET_FORMAT = "<+secrets.getValue(\"%s\")>";
  String TRIGGER_TAG_VALUE_DEFAULT = "<+trigger.artifact.build>";
}
