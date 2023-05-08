/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamStepHelper {
  public void verifyPluginImageIsProvider(ParameterField<String> image) {
    if (ParameterField.isNull(image) || image.getValue() == null) {
      throw new InvalidRequestException("Plugin Image must be provided");
    }
  }
}
