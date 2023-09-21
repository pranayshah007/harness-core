/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class SpotInstErrorHandlerTest extends CategoryTest {
  private final String instancehealthiness_error_file_path =
      "960-api-services/src/test/resources/__files/spot/instanceHealthiness-error.json";
  private final String instanceHealthiness_error_invalid_req_file_path =
      "960-api-services/src/test/resources/__files/spot/instanceHealthiness-error-invalid-req.json";
  private final String instanceHealthiness_error_invalid_res_file_path =
      "960-api-services/src/test/resources/__files/spot/instanceHealthiness-error-invalid-res.json";
  private final String random_error = "Some random error";

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGenerateException_instancehealthiness() throws IOException {
    String error = loadContent(instancehealthiness_error_file_path);
    WingsException exception = SpotInstErrorHandler.generateException(error);
    assertThat(exception.getMessage()).contains("An error occurred when calling InstanceHealthiness API");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGenerateException_not_json() {
    WingsException exception = SpotInstErrorHandler.generateException(random_error);
    assertThat(exception.getMessage()).isEqualTo(random_error);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGenerateException_instancehealthiness_invalid_req() throws IOException {
    String error = loadContent(instanceHealthiness_error_invalid_req_file_path);
    WingsException exception = SpotInstErrorHandler.generateException(error);
    assertThat(exception.getMessage()).isEqualTo(error);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGenerateException_instancehealthiness_invalid_res() throws IOException {
    String error = loadContent(instanceHealthiness_error_invalid_res_file_path);
    WingsException exception = SpotInstErrorHandler.generateException(error);
    assertThat(exception.getMessage()).isEqualTo(error);
  }

  private String loadContent(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
