package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CfRunPluginCommandRequestNG extends AbstractTasTaskRequest {
  String renderedScriptString;
  List<String> filePathsInScript;
  @Expression(ALLOW_SECRETS) List<FileData> fileDataList;
  @Expression(ALLOW_SECRETS) String repoRoot;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Builder
  public CfRunPluginCommandRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin,

      String renderedScriptString, List<String> filePathsInScript, List<FileData> fileDataList, String repoRoot,
      List<EncryptedDataDetail> encryptedDataDetails) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);

    this.renderedScriptString = renderedScriptString;
    this.filePathsInScript = filePathsInScript;
    this.fileDataList = fileDataList;
    this.repoRoot = repoRoot;
    this.encryptedDataDetails = encryptedDataDetails;
  }
}
