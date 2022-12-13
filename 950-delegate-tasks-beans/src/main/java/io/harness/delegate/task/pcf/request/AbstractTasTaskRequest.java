package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractTasTaskRequest implements CfCommandRequestNG {
  @Getter @Setter Integer timeoutIntervalInMin;
  @Getter @Setter @NonNull public String accountId;
  @Getter @Setter String commandName;
  @Getter @Setter CfCommandTypeNG cfCommandTypeNG;
  @Getter @Setter CommandUnitsProgress commandUnitsProgress;
  @Getter @Setter @NotNull TasInfraConfig tasInfraConfig;
  @Getter @Setter boolean useCfCLI;
  @Getter @Setter @NotNull CfCliVersion cfCliVersion;
}
