package io.harness.cdng.artifact.bean.yaml;

import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactConfigHelper {
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
