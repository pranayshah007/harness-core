/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.pms.yaml.ParameterField;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ContainerStepResolverUtils {
  public static OSType resolveOSType(ParameterField<OSType> osType) {
    if (osType == null || osType.isExpression() || osType.getValue() == null) {
      return OSType.Linux;
    } else {
      return OSType.fromString(osType.fetchFinalValue().toString());
    }
  }

  public static String resolveImagePullPolicy(ParameterField<ImagePullPolicy> pullPolicy) {
    if (pullPolicy == null || pullPolicy.isExpression() || pullPolicy.getValue() == null) {
      return null;
    } else {
      return ImagePullPolicy.fromString(pullPolicy.fetchFinalValue().toString()).getYamlName();
    }
  }

  public static CIShellType resolveShellType(ParameterField<CIShellType> shellType) {
    if (shellType == null || shellType.isExpression() || shellType.getValue() == null) {
      return CIShellType.SH;
    } else {
      return CIShellType.fromString(shellType.fetchFinalValue().toString());
    }
  }
}
