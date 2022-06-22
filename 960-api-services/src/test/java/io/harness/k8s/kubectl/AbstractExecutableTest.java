/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.rule.OwnerRule.BOGDAN;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.K8sConstants;
import io.harness.rule.Owner;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Utils.class})
@OwnedBy(HarnessTeam.CDP)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
public class AbstractExecutableTest {
  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldAddGoogleCredentialsIfFileExists() throws Exception {
    // given
    PowerMockito.mockStatic(Utils.class);

    Path tempDirectory = Files.createTempDirectory("abstractExecTest");
    Path gcpFile = Files.createFile(tempDirectory.resolve(K8sConstants.GCP_JSON_KEY_FILE_NAME));

    // when
    dummyExecutable().execute(tempDirectory.toString(), NULL_OUTPUT_STREAM, NULL_OUTPUT_STREAM, false);

    // then
    Map<String, String> capturedEnvironment = captureEnvironment();
    assertThat(capturedEnvironment).containsEntry("GOOGLE_APPLICATION_CREDENTIALS", gcpFile.toString());
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotAddGoogleCredentialsEnvVarIfFileNotExist() throws Exception {
    // given
    PowerMockito.mockStatic(Utils.class);
    Path tempDirectory = Files.createTempDirectory("abstractExecTest");

    // when
    dummyExecutable().execute(tempDirectory.toString(), NULL_OUTPUT_STREAM, NULL_OUTPUT_STREAM, false);

    // then
    Map<String, String> environment = captureEnvironment();
    assertThat(environment).doesNotContainKey("GOOGLE_APPLICATION_CREDENTIALS");
  }

  private Map<String, String> captureEnvironment() throws Exception {
    ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    PowerMockito.verifyStatic(Utils.class, Mockito.times(1));
    Utils.executeScript(
        anyString(), anyString(), any(OutputStream.class), any(OutputStream.class), mapArgumentCaptor.capture());
    return (Map<String, String>) mapArgumentCaptor.getValue();
  }

  private AbstractExecutable dummyExecutable() {
    return new AbstractExecutable() {
      @Override
      public String command() {
        return "myCommand";
      }
    };
  }
}
