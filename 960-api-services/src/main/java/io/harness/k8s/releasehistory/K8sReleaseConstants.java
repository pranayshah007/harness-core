/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;
import static io.harness.k8s.model.HarnessLabelValues.colorBlue;
import static io.harness.k8s.model.HarnessLabelValues.colorGreen;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@UtilityClass
public class K8sReleaseConstants {
  public static final String RELEASE_SECRET_NAME_PREFIX = "harness.release";
  public static final String RELEASE_KEY = "release";
  public static final String RELEASE_NAME_DELIMITER = ".";
  public static final String SECRET_LABEL_DELIMITER = ",";
  public static final String RELEASE_NUMBER_LABEL_KEY = "release-number";
  public static final String RELEASE_OWNER_LABEL_KEY = "owner";
  public static final String RELEASE_OWNER_LABEL_VALUE = "harness";
  public static final String RELEASE_STATUS_LABEL_KEY = "status";
  public static final String RELEASE_SECRET_TYPE_KEY = "type";
  public static final String RELEASE_SECRET_TYPE_VALUE = "harness.io/release/v2";
  public static final String RELEASE_PRUNING_ENABLED_KEY = "harness.io/pruning-enabled";
  public static final String RELEASE_SECRET_RELEASE_COLOR_KEY = "color";
  public static final String RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY = "harness.io/bg-environment";
  public static final String RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY = "harness.io/manifest-hash";

  public static final String RELEASE_SECRET_HELM_CHART_NAME_KEY = "metadata.helm.harness.io/chart-name";
  public static final String RELEASE_SECRET_HELM_CHART_VERSION_KEY = "metadata.helm.harness.io/chart-version";
  public static final String RELEASE_SECRET_HELM_CHART_REPO_URL_KEY = "metadata.helm.harness.io/chart-repo-url";
  public static final String RELEASE_SECRET_HELM_CHART_SUB_CHART_PATH_KEY =
      "metadata.helm.harness.io/chart-sub-chart-path";
  public static final Map<String, String> RELEASE_SECRET_TYPE_MAP =
      Map.of(RELEASE_SECRET_TYPE_KEY, RELEASE_SECRET_TYPE_VALUE);
  public static final Map<String, String> RELEASE_SECRET_LABELS_MAP =
      Map.of(RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE);
  public static final int RELEASE_HISTORY_LIMIT = 2;
  public static final String RELEASE_LABEL_QUERY_SET_FORMAT = "%s in (%s)";
  public static final String RELEASE_LABEL_QUERY_LIST_FORMAT = "%s=%s";
  public static final Set<String> BLUE_GREEN_COLORS = ImmutableSet.of(colorBlue, colorGreen);

  public static final String RELEASE_SECRET_ANNOTATION_SERVICE = "harness.io/service";
  public static final String RELEASE_SECRET_ANNOTATION_ENV = "harness.io/env";
  public static final String RELEASE_SECRET_ANNOTATION_INFRA_ID = "harness.io/infra";
  public static final String RELEASE_SECRET_ANNOTATION_INFRA_KEY = "harness.io/infra-key";
}
