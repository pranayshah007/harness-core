/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages;


import io.harness.annotations.dev.OwnedBy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static io.harness.annotations.dev.HarnessTeam.IDP;

@OwnedBy(IDP)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IDPStepSpecTypeConstants {
    public static final String IDP_STAGE = "IDPStage";

}
