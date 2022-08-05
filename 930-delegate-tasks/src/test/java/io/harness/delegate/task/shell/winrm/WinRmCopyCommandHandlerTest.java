package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJAN;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.delegate.task.winrm.FileBasedWinRmExecutorNG;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.ssh.FileSourceType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.Silent.class)
public class WinRmCopyCommandHandlerTest {
  @Mock private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private SecretDecryptionService secretDecryptionService;
  ;
  final List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

  @InjectMocks private WinRmCopyCommandHandler winRmCopyCommandHandler;

  final CopyCommandUnit copyArtifactCommandUnit =
      CopyCommandUnit.builder().name("test").sourceType(FileSourceType.ARTIFACT).destinationPath("/test").build();
  final CopyCommandUnit copyConfigCommandUnit =
      CopyCommandUnit.builder().name("test").sourceType(FileSourceType.CONFIG).destinationPath("/test").build();

  @Before
  public void setup() {
    when(winRmExecutorFactoryNG.getFiledBasedWinRmExecutor(any(), anyBoolean(), any(), any()))
        .thenReturn(fileBasedWinRmExecutorNG);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(
            SecretConfigFile.builder()
                .encryptedConfigFile(
                    SecretRefData.builder().identifier("identifier").decryptedValue(new char[] {'a', 'b'}).build())
                .build());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigWithWinRmExecutor() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters = getWinrmTaskParameters(copyConfigCommandUnit, outputVariables);
    when(fileBasedWinRmExecutorNG.copyConfigFiles(any(ConfigFileParameters.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus result = winRmCopyCommandHandler.handle(
        winrmTaskParameters, copyConfigCommandUnit, iLogStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithWinRmExecutor() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters = getWinrmTaskParameters(copyArtifactCommandUnit, outputVariables);

    when(fileBasedWinRmExecutorNG.copyArtifacts(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus result = winRmCopyCommandHandler.handle(
        winrmTaskParameters, copyArtifactCommandUnit, iLogStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  private WinrmTaskParameters getWinrmTaskParameters(
      CopyCommandUnit copyConfigCommandUnit, List<String> outputVariables) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    return WinrmTaskParameters.builder()
        .commandUnits(Collections.singletonList(copyConfigCommandUnit))
        .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
        .executeOnDelegate(true)
        .disableWinRMCommandEncodingFFSet(true)
        .outputVariables(outputVariables)
        .fileDelegateConfig(
            FileDelegateConfig.builder()
                .stores(singletonList(
                    HarnessStoreDelegateConfig.builder()
                        .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                                       .fileContent("hello world")
                                                       .fileName("test.txt")
                                                       .fileSize(11L)
                                                       .build(),
                            ConfigFileParameters.builder()
                                .fileName("secret-ref")
                                .isEncrypted(true)
                                .encryptionDataDetails(singletonList(encryptedDataDetail))
                                .secretConfigFile(
                                    SecretConfigFile.builder()
                                        .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                        .build())
                                .build()))
                        .build()))
                .build())
        .build();
  }
}
