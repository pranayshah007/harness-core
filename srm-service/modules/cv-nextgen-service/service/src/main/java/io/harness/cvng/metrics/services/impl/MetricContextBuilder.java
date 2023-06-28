/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.services.impl;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.metrics.beans.AnalysisStateMachineContext;
import io.harness.cvng.metrics.beans.DataCollectionTaskMetricContext;
import io.harness.cvng.metrics.beans.LETaskMetricContext;
import io.harness.cvng.metrics.beans.VerificationTaskMetricContext;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.beans.AccountMetricContext;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class MetricContextBuilder {
  private static Map<Class<?>, ObjectContextBuilder<?>> OBJECT_CONTEXT_BUILDER_MAP = new HashMap<>();
  static {
    addToObjContextMap(DataCollectionTask.class,
        dataCollectionTask
        -> new DataCollectionTaskMetricContext(dataCollectionTask.getAccountId(),
            dataCollectionTask.getType().name().toLowerCase(),
            dataCollectionTask.getDataCollectionInfo().getClass().getSimpleName().replace("DataCollectionInfo", ""),
            dataCollectionTask.getRetryCount()));
    addToObjContextMap(CVNGStepTask.class, cvngStepTask -> new AccountMetricContext(cvngStepTask.getAccountId()));
    addToObjContextMap(VerificationJobInstance.class,
        verificationJobInstance -> new AccountMetricContext(verificationJobInstance.getAccountId()));
    addToObjContextMap(LearningEngineTask.class,
        learningEngineTask
        -> new LETaskMetricContext(learningEngineTask.getAccountId(),
            learningEngineTask.getType().toString().toLowerCase(),
            learningEngineTask.getTaskStatus().name().toLowerCase(),
            Duration.between(learningEngineTask.getAnalysisStartTime(), learningEngineTask.getAnalysisEndTime())));
    addToObjContextMap(
        AnalysisStateMachine.class, analysisStateMachine -> new AnalysisStateMachineContext(analysisStateMachine));
    addToObjContextMap(AnalysisOrchestrator.class,
        analysisOrchestrator -> new AccountMetricContext(analysisOrchestrator.getAccountId()));
    addToObjContextMap(VerificationTask.class, VerificationTaskMetricContext::new);
  }

  private static <T> void addToObjContextMap(Class<T> clazz, ObjectContextBuilder<T> objectContextBuilder) {
    OBJECT_CONTEXT_BUILDER_MAP.put(clazz, objectContextBuilder);
  }

  public <T> AutoMetricContext getContext(T obj, Class<T> clazz) {
    Preconditions.checkState(OBJECT_CONTEXT_BUILDER_MAP.containsKey(clazz),
        "Object context builder is not defined for class: %s", obj.getClass());
    ObjectContextBuilder<T> objectContextBuilder = (ObjectContextBuilder<T>) OBJECT_CONTEXT_BUILDER_MAP.get(clazz);
    return objectContextBuilder.create(obj);
  }

  private interface ObjectContextBuilder<T> {
    AutoMetricContext create(T obj);
  }
}
