/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactConfigHelper {
  public void checkTagAndTagRegex(ParameterField<String> parameterField1, ParameterField<String> parameterField2) {
    if (checkBothNullOrInput(parameterField1, parameterField2)) {
      throw new InvalidRequestException("value for tag and tagRegex is empty or not provided");
    }
  }

  public void checkVersionAndVersionRegex(
      ParameterField<String> parameterField1, ParameterField<String> parameterField2) {
    if (checkBothNullOrInput(parameterField1, parameterField2)) {
      throw new InvalidRequestException("value for version and versionRegex is empty or not provided");
    }
  }

  public void checkFilePathAndFilePathRegex(
      ParameterField<String> parameterField1, ParameterField<String> parameterField2) {
    if (checkBothNullOrInput(parameterField1, parameterField2)) {
      throw new InvalidRequestException("value for filePath and filePathRegex is empty or not provided");
    }
  }

  public boolean checkBothNullOrInput(ParameterField<String> parameterField1, ParameterField<String> parameterField2) {
    if (checkNullOrInput(parameterField1) && checkNullOrInput(parameterField2)) {
      return true;
    }
    return false;
  }
  public boolean checkNullOrInput(ParameterField<String> parameterField) {
    if (parameterField == null || parameterField.fetchFinalValue() == null) {
      return true;
    }
    String val = (String) parameterField.fetchFinalValue();
    if (EmptyPredicate.isEmpty(val) || NGExpressionUtils.matchesInputSetPattern(val)) {
      return true;
    }
    return false;
  }
}