/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.VLICA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;
import io.harness.terraform.beans.TerraformVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class TerraformHelperUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testgenerateCommandFlagsString() {
    List<String> listofArgs = Arrays.asList("arg1", "arg2");
    String result = TerraformHelperUtils.generateCommandFlagsString(listofArgs, "-command");
    assertThat(result).isNotNull();
    result.equals("-command=arg1 -command=arg2");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGenerateCommandFlagsStringWithEmptyListOfArguments() {
    List<String> emptyListOfArgs = new ArrayList<>();
    String result = TerraformHelperUtils.generateCommandFlagsString(emptyListOfArgs, "-command");
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGenerateCommandFlagsStringContainingEmptyArguments() {
    List<String> listOfArgs = Arrays.asList("arg1", "", "", "arg2");
    String result = TerraformHelperUtils.generateCommandFlagsString(listOfArgs, "-command");
    assertThat(result).isNotNull();
    result.equals("-command=arg1 -command=arg2");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testgetTerraformStateFile() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformStateFile";
    String workspace = "tfworkspace";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    FileUtils.touch(new File(format("%s/terraform.tfstate.d/%s/terraform.tfstate", scriptDirectory, workspace)));
    File tfFile = TerraformHelperUtils.getTerraformStateFile(scriptDirectory, workspace);
    assertThat(tfFile).isNotNull();
    assertThat(tfFile.getName()).isEqualTo("terraform.tfstate");
    Files.deleteIfExists(Paths.get(tfFile.getPath()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetAutoApproveArgument() {
    assertThat(TerraformHelperUtils.getAutoApproveArgument(TerraformVersion.create(0, 7, 3))).isEqualTo("-force");
    assertThat(TerraformHelperUtils.getAutoApproveArgument(TerraformVersion.create(0, 15, 2)))
        .isEqualTo("-auto-approve");
    assertThat(TerraformHelperUtils.getAutoApproveArgument(TerraformVersion.create(1, 4, 1)))
        .isEqualTo("-auto-approve");
  }
}
