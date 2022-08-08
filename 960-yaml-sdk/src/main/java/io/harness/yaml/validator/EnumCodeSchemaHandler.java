/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.validator;

import io.harness.yaml.utils.SchemaValidationUtils;

import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidatorTypeCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EnumCodeSchemaHandler {
  public List<ValidationMessage> handle(List<ValidationMessage> validationMessages) {
    Map<String, List<ValidationMessage>> pathMap = SchemaValidationUtils.getValidationPathMap(validationMessages);
    List<ValidationMessage> processedValidationMsg = new ArrayList<>();
    // Iterating over all FQN's and processing error messages for each FQN.
    for (List<ValidationMessage> validationMessageList : pathMap.values()) {
      List<String> arguments = new ArrayList<>();
      for (ValidationMessage validationMessage : validationMessageList) {
        arguments.addAll(
            Arrays.asList(SchemaValidationUtils.removeParenthesisFromArguments(validationMessage.getArguments())));
      }
      ValidationMessage validationMessage = validationMessageList.get(0);
      processedValidationMsg.add(ValidationMessage.of(validationMessage.getType(), ValidatorTypeCode.ENUM,
          validationMessage.getPath(), Arrays.toString(arguments.toArray())));
    }
    return processedValidationMsg;
  }
}
