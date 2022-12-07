package io.harness.steps.plugin;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.steps.plugin.ContainerStepPassThroughData")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContainerStepPassThroughData implements PassThroughData {}
