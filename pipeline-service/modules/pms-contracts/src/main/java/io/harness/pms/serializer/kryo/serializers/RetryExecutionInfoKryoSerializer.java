/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class RetryExecutionInfoKryoSerializer extends ProtobufKryoSerializer<RetryExecutionInfo> {
  private static RetryExecutionInfoKryoSerializer instance;

  private RetryExecutionInfoKryoSerializer() {}

  public static synchronized RetryExecutionInfoKryoSerializer getInstance() {
    if (instance == null) {
      instance = new RetryExecutionInfoKryoSerializer();
    }
    return instance;
  }
}
