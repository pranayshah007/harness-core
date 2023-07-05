/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;


@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@TypeAlias("CustomStageSpecParams")
@RecasterAlias("io.harness.idp.pipeline.stages.IDPStageSpecParams")
public class IDPStageSpecParams implements SpecParameters {
    String childNodeID;

    public static IDPStageSpecParams getStepParameters(String childNodeID) {
        return IDPStageSpecParams.builder().childNodeID(childNodeID).build();
    }
}
