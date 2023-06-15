/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.utils.Utils.isInstanceOf;

import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StoreConfigHelper {
  public static final String CONNECTOR_REF = "connectorRef";
  public static final String BRANCH = "branch";
  public static final String COMMIT_ID = "commitId";
  public static final String FOLDER_PATH = "folderPath";

  /**
   * this method checks if a parameter field of list of strings is null or contains all strings as null or "<+input>"
   * type Also, note that in some cases, the final value of parameter field can be a string instead of a list of
   * strings, so the first if block checks for that.
   */
  public boolean checkListOfStringsParameterNullOrInput(ParameterField<List<String>> parameterField) {
    if (parameterField == null || parameterField.fetchFinalValue() == null) {
      return true;
    }
    if (isInstanceOf(String.class, parameterField.fetchFinalValue())) {
      String path = (String) parameterField.fetchFinalValue();
      if (checkStringNullOrInput(path)) {
        return true;
      }
    } else if (isInstanceOf(List.class, parameterField.fetchFinalValue())) {
      List<String> paths = (List<String>) parameterField.fetchFinalValue();
      for (String path : paths) {
        if (!checkStringNullOrInput(path)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean checkStringParameterNullOrInput(ParameterField<String> parameterField) {
    if (parameterField == null || parameterField.fetchFinalValue() == null) {
      return true;
    }
    String val = (String) parameterField.fetchFinalValue();
    return checkStringNullOrInput(val);
  }

  public boolean checkStringNullOrInput(String parameterField) {
    if (EmptyPredicate.isEmpty(parameterField) || NGExpressionUtils.matchesInputSetPattern(parameterField)) {
      return true;
    }
    return false;
  }

  public Set<String> validateGitStoreType(ParameterField<String> connectorRef, ParameterField<String> folderPath,
      ParameterField<List<String>> paths, ParameterField<String> branch, ParameterField<String> commitId,
      FetchType gitFetchType) {
    final Set<String> invalidParameters = new HashSet<>();

    if (checkStringParameterNullOrInput(connectorRef)) {
      invalidParameters.add(CONNECTOR_REF);
    }
    if ((FetchType.BRANCH).equals(gitFetchType)) {
      if (checkStringParameterNullOrInput(branch)) {
        invalidParameters.add(BRANCH);
      }
    } else {
      if (checkStringParameterNullOrInput(commitId)) {
        invalidParameters.add(COMMIT_ID);
      }
    }

    if (StoreConfigHelper.checkStringParameterNullOrInput(folderPath)
        && StoreConfigHelper.checkListOfStringsParameterNullOrInput(paths)) {
      invalidParameters.add(FOLDER_PATH);
    }

    return invalidParameters;
  }
}
