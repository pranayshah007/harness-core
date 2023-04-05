/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

public class NoopStepGroupInfra implements StepGroupInfra {
  @Builder.Default @NotNull @Getter private StepGroupInfra.Type type = Type.NO_OP;
}
