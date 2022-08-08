/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common.validator;

import software.wings.utils.ArtifactType;

import java.util.Arrays;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ArtifactTypeSubsetValidator implements ConstraintValidator<ArtifactTypeSubset, ArtifactType> {
  ArtifactType[] subset;

  @Override
  public void initialize(ArtifactTypeSubset artifactTypeSubset) {
    this.subset = artifactTypeSubset.anyOf();
  }

  @Override
  public boolean isValid(ArtifactType value, ConstraintValidatorContext constraintValidatorContext) {
    return value == null || Arrays.asList(subset).contains(value);
  }
}
