package io.harness.cdng.k8s.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData")
public class CustomFetchResponsePassThroughData implements PassThroughData {
  String errorMsg;
  UnitProgressData unitProgressData;
}
