/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.exception.ngexception.CIStageExecutionException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ValidationUtils {
  private static String serviceRegex = "^[a-zA-Z][a-zA-Z0-9_]*$";

  public void validateVmInfraDependencies(List<DependencyElement> dependencyElements) {
    if (isEmpty(dependencyElements)) {
      return;
    }

    for (DependencyElement dependencyElement : dependencyElements) {
      if (dependencyElement == null) {
        continue;
      }
      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        CIServiceInfo ciServiceInfo = (CIServiceInfo) dependencyElement.getDependencySpecType();
        Pattern p = Pattern.compile(serviceRegex);
        Matcher m = p.matcher(ciServiceInfo.getIdentifier());
        if (!m.find()) {
          throw new CIStageExecutionException(format(
              "Service dependency identifier %s does not match regex %s", ciServiceInfo.getIdentifier(), serviceRegex));
        }
      }
    }
  }
}
