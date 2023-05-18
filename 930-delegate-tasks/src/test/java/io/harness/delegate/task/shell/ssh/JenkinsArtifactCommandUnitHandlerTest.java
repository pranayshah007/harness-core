/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.service.impl.jenkins.JenkinsUtils;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class JenkinsArtifactCommandUnitHandlerTest extends CategoryTest {
  @Mock JenkinsUtils jenkinsUtil;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock JenkinsUserNamePasswordDTO decryptableEntity;
  @Mock LogCallback logCallback;
  @Mock SecretRefData secretRefData;
  @Mock Jenkins jenkins;
  @Inject @InjectMocks JenkinsArtifactCommandUnitHandler handler;

  final String fileContent = "some content";
  InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
    doReturn(secretRefData).when(decryptableEntity).getPasswordRef();
    when(jenkinsUtil.getJenkins(any())).thenReturn(jenkins);
    handler = new JenkinsArtifactCommandUnitHandler(jenkinsUtil, secretDecryptionService);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDownloadFromRemoteRepo() throws IOException, URISyntaxException {
    SshExecutorFactoryContext context = getContext();
    when(jenkins.downloadArtifact(any(), any(), any())).thenReturn(new Pair<String, InputStream>() {
      @Override
      public String getLeft() {
        return "result";
      }

      @Override
      public InputStream getRight() {
        return is;
      }

      @Override
      public InputStream setValue(InputStream value) {
        return value;
      }
    });
    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);

    String resultText = IOUtils.toString(result, Charset.defaultCharset());
    assertThat(result).isNotNull();
    assertThat(resultText).isEqualTo(fileContent);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldDownloadFromRemoteRepoReturnsNull() throws IOException {
    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(AwsS3ArtifactDelegateConfig.builder().build())
                                            .build();
    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldDownloadFromRemoteRepoFails() throws IOException, URISyntaxException {
    SshExecutorFactoryContext context = getContext();
    when(jenkins.downloadArtifact(any(), any(), any())).thenThrow(new URISyntaxException("oops", ""));
    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSize() throws IOException, URISyntaxException {
    SshExecutorFactoryContext context = getContext();
    doReturn(50L).when(jenkins).getFileSize(anyString(), anyString(), anyString());
    Long result = handler.getArtifactSize(context, logCallback);
    assertThat(result).isEqualTo(50L);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSizeReturnsZero() {
    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(AwsS3ArtifactDelegateConfig.builder().build())
                                            .build();
    Long result = handler.getArtifactSize(context, logCallback);
    assertThat(result).isEqualTo(0L);
  }

  private SshExecutorFactoryContext getContext() {
    JenkinsUserNamePasswordDTO creds =
        JenkinsUserNamePasswordDTO.builder()
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();

    JenkinsAuthenticationDTO auth =
        JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).credentials(creds).build();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(JenkinsConnectorDTO.builder().jenkinsUrl("http://jenkins").auth(auth).build())
            .build();

    return SshExecutorFactoryContext.builder()
        .artifactDelegateConfig(JenkinsArtifactDelegateConfig.builder()
                                    .artifactPath("/path/to/artifact")
                                    .jobName("test")
                                    .build("123")
                                    .identifier("testIdentifier")
                                    .encryptedDataDetails(Collections.emptyList())
                                    .connectorDTO(connectorInfoDTO)
                                    .build())
        .build();
  }
}
