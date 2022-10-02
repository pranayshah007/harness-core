/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.entities;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Data
@NoArgsConstructor
@Slf4j
public class CanaryAnalysisState extends TimeSeriesAnalysisState {
    protected Set<String> controlHosts;
    protected Set<String> testHosts;
    protected VerificationJobInstance verificationJobInstance;
    protected LearningEngineTask.LearningEngineTaskType learningEngineTaskType;

    @Override
    public StateType getType() {
        return StateType.CANARY_ANALYSIS_STATE;
    }
}
