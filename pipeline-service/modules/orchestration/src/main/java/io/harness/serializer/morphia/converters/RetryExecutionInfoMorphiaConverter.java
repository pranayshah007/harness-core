/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia.converters;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.persistence.converters.ProtoMessageConverter;
import io.harness.pms.contracts.plan.RetryExecutionInfo;

import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class RetryExecutionInfoMorphiaConverter extends ProtoMessageConverter<RetryExecutionInfo> {
  public RetryExecutionInfoMorphiaConverter() {
    super(RetryExecutionInfo.class);
  }
}
