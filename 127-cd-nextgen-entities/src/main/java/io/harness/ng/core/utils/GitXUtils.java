/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ScmException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class GitXUtils {
  public static final String BOOLEAN_TRUE_VALUE = "true";

  public boolean parseLoadFromCacheHeaderParam(String loadFromCache) {
    if (isEmpty(loadFromCache)) {
      return false;
    } else {
      return BOOLEAN_TRUE_VALUE.equalsIgnoreCase(loadFromCache);
    }
  }

  public String getErrorMessageForGitSimplificationNotEnabled(String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      return format("Remote git simplification was not enabled for Project [%s] in Organisation [%s]",
          projectIdentifier, orgIdentifier);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      return format(
          "Remote git simplification or feature flag was not enabled for Organisation [%s] or Account", orgIdentifier);
    }
    return "Remote git simplification or feature flag was not enabled for Account";
  }

  public ScmException getScmExceptionIfExists(Throwable ex) {
    while (ex != null) {
      if (ex instanceof ScmException) {
        return (ScmException) ex;
      }
      ex = ex.getCause();
    }
    return null;
  }
}
