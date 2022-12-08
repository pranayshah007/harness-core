package io.harness.delegate.task.pcf.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfRunPluginCommandRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  @NotNull TasInfraConfig tasInfraConfig;
  boolean useCfCLI;
  String renderedScriptString;
  List<String> filePathsInScript;
  @Expression(ALLOW_SECRETS) List<FileData> fileDataList;
  @Expression(ALLOW_SECRETS) String repoRoot;
  @NotNull CfCliVersionNG cfCliVersionNG;
  CfCliVersion cfCliVersion;
  List<EncryptedDataDetail> encryptedDataDetails;
}
