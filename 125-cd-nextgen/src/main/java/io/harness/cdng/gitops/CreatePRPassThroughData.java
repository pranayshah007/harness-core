package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("CreatePRPassThroughData")
@RecasterAlias("io.harness.cdng.gitOps.CreatePRPassThroughData")
public class CreatePRPassThroughData implements PassThroughData {
  Map<String, String> stringMap;
  @Setter List<String> filePaths;
}
