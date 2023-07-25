/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.cdng.execution;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "className")
@JsonSubTypes({ @JsonSubTypes.Type(value = K8sStepInstanceInfo.class, name = "K8sStepInstanceInfo") })
@RecasterAlias("io.harness.delegate.cdng.execution.StepInstanceInfo")
public interface StepInstanceInfo {
  String getInstanceName();
}
