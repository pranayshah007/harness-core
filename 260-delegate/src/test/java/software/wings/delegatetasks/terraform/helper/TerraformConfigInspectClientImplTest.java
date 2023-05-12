/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.terraform.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJANA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.clienttools.TerraformConfigInspectVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;

import software.wings.delegatetasks.terraform.TerraformConfigInspectClient;

import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformConfigInspectClientImplTest extends CategoryTest {
  private static final String GIT_REPO_DIRECTORY = "repository/terraformTest";
  private static final boolean useLatestVersion = true;
  private MockedStatic<InstallUtils> aStatic;
  TerraformConfigInspectClientImpl terraformConfigInspectClientSpy = spy(new TerraformConfigInspectClientImpl());

  @Before
  public void setUp() {
    aStatic = Mockito.mockStatic(InstallUtils.class);
    aStatic.when(() -> InstallUtils.getPath(any(), any())).thenAnswer(invocationOnMock -> "/tmp/dummypath/tool");
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void parseFieldsUnderBlock() throws IOException {
    String terraformConfigInspectClientResponse = "{\n"
        + "   \"path\":\"repository/terraformTest/scriptPath\",\n"
        + "   \"variables\":{\n"
        + "      \"a\":{\n"
        + "         \"name\":\"a\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":1\n"
        + "         }\n"
        + "      },\n"
        + "      \"sleep\":{\n"
        + "         \"name\":\"sleep\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":2\n"
        + "         }\n"
        + "      }\n"
        + "   },\n"
        + "   \"outputs\":{\n"
        + "      \"a\":{\n"
        + "         \"name\":\"a\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":11\n"
        + "         }\n"
        + "      }\n"
        + "   },\n"
        + "   \"required_providers\":{\n"
        + "      \"null\":[\n"
        + "         \n"
        + "      ]\n"
        + "   },\n"
        + "   \"managed_resources\":{\n"
        + "      \"null_resource.delay\":{\n"
        + "         \"mode\":\"managed\",\n"
        + "         \"type\":\"null_resource\",\n"
        + "         \"name\":\"delay\",\n"
        + "         \"provider\":{\n"
        + "            \"name\":\"null\"\n"
        + "         },\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":4\n"
        + "         }\n"
        + "      }\n"
        + "   },\n"
        + "   \"data_resources\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"module_calls\":{\n"
        + "      \n"
        + "   }\n"
        + "}";
    when(terraformConfigInspectClientSpy.executeShellCommand(anyString()))
        .thenReturn(terraformConfigInspectClientResponse);
    List<String> inputVariables = terraformConfigInspectClientSpy.parseFieldsUnderBlock(GIT_REPO_DIRECTORY,
        TerraformConfigInspectClient.BLOCK_TYPE.VARIABLES.name().toLowerCase(), TerraformConfigInspectVersion.V1_1);
    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    assertThat(inputVariables).contains("sleep", "a");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void parseFieldsUnderBlockIncorrectSyntax() throws IOException {
    String terraformConfigInspectClientResponse = "{\n"
        + "   \"path\":\"repository/terraformTest/scriptPath\",\n"
        + "   \"variables\":{\n"
        + "      \"access_key\":{\n"
        + "         \"name\":\"access_key\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":1\n"
        + "         }\n"
        + "      },\n"
        + "      \"secret_key\":{\n"
        + "         \"name\":\"secret_key\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":2\n"
        + "         }\n"
        + "      }\n"
        + "   },\n"
        + "   \"outputs\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"required_providers\":{\n"
        + "      \"aws\":[\n"
        + "         \n"
        + "      ]\n"
        + "   },\n"
        + "   \"managed_resources\":{\n"
        + "      \"aws_s3_bucket.bucket\":{\n"
        + "         \"mode\":\"managed\",\n"
        + "         \"type\":\"aws_s3_bucket\",\n"
        + "         \"name\":\"bucket\",\n"
        + "         \"provider\":{\n"
        + "            \"name\":\"aws\"\n"
        + "         },\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":10\n"
        + "         }\n"
        + "      }\n"
        + "   },\n"
        + "   \"data_resources\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"module_calls\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"diagnostics\":[\n"
        + "      {\n"
        + "         \"severity\":\"error\",\n"
        + "         \"summary\":\"Missing name for resource\",\n"
        + "         \"detail\":\"All resource blocks must have 2 labels (type, name).\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":13\n"
        + "         }\n"
        + "      }\n"
        + "   ]\n"
        + "}";
    when(terraformConfigInspectClientSpy.executeShellCommand(anyString()))
        .thenReturn(terraformConfigInspectClientResponse);
    List<String> inputVariables = terraformConfigInspectClientSpy.parseFieldsUnderBlock(GIT_REPO_DIRECTORY,
        TerraformConfigInspectClient.BLOCK_TYPE.VARIABLES.name().toLowerCase(), TerraformConfigInspectVersion.V1_1);
    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    assertThat(inputVariables).contains("access_key", "secret_key");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void parseFieldsUnderBlockError() throws IOException {
    String invalidSyntax = "variable \"a {}\n"
        + "variable \"sleep\" {}\n"
        + "\n"
        + "resource \"null_resource\" \"delay\" {\n"
        + "  provisioner \"local-exec\" {\n"
        + "    command = var.sleep\n"
        + "    interpreter = [\"/bin/sleep\"]\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "output \"a\" {\n"
        + "  value = var.a\n"
        + "}\n";
    String terraformConfigInspectClientResponse = "{\n"
        + "   \"path\":\"repository/terraformTest/scriptPath\",\n"
        + "   \"variables\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"outputs\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"required_providers\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"managed_resources\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"data_resources\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"module_calls\":{\n"
        + "      \n"
        + "   },\n"
        + "   \"diagnostics\":[\n"
        + "      {\n"
        + "         \"severity\":\"error\",\n"
        + "         \"summary\":\"Invalid multi-line string\",\n"
        + "         \"detail\":\"Quoted strings may not be split over multiple lines. To produce a multi-line string, either use the \\\\n escape to represent a newline character or use the \\\"heredoc\\\" multi-line template syntax.\",\n"
        + "         \"pos\":{\n"
        + "            \"filename\":\"repository/terraformTest/scriptPath/config.tf\",\n"
        + "            \"line\":1\n"
        + "         }\n"
        + "      }\n"
        + "   ]\n"
        + "}";
    when(terraformConfigInspectClientSpy.executeShellCommand(anyString()))
        .thenReturn(terraformConfigInspectClientResponse);
    assertThatThrownBy(() -> {
      terraformConfigInspectClientSpy.parseFieldsUnderBlock(GIT_REPO_DIRECTORY,
          TerraformConfigInspectClient.BLOCK_TYPE.VARIABLES.name().toLowerCase(), TerraformConfigInspectVersion.V1_1);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Quoted strings may not be split over multiple lines.")
        .hasMessageEndingWith("File: config.tf Line: 1");
    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
  }
}
