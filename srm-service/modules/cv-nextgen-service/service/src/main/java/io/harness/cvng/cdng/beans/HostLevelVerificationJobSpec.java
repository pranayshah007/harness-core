/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Data
@SuperBuilder
@NoArgsConstructor
public abstract class HostLevelVerificationJobSpec extends VerificationJobSpec {
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH, value = "Possible values: [true, false]")
  ParameterField<Boolean> shouldUseCDNodes;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH,
      value = "Enter the reg-ex pattern that all test-node names should follow")
  ParameterField<String> testNodeRegExPattern;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH,
      value = "Enter the reg-ex pattern that all control-node names should follow")
  ParameterField<String> controlNodeRegExPattern;

  public void validate() {
    super.validate();
    validateRegex(testNodeRegExPattern, "Test");
    validateRegex(controlNodeRegExPattern, "Control");
  }

  private void validateRegex(ParameterField<String> pattern, String nodeType) {
    if (pattern != null && StringUtils.isNotEmpty(pattern.getValue())) {
      try {
        Pattern.compile(pattern.getValue());
      } catch (PatternSyntaxException ex) {
        throw new IllegalArgumentException(nodeType + " Node Regex syntax isn't right.");
      }
    }
  }
}
