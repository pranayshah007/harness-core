/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.model.SecretRequest;
import io.harness.spec.server.ng.model.SecretResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import net.sf.json.test.JSONAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class SecretApiUtilsTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Validator validator;

  private String testFilesBasePath = "120-ng-manager/src/test/resources/server/stub/secret/";
  private SecretApiUtils secretApiUtils;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    secretApiUtils = new SecretApiUtils(validator);
  }

  private void testSecretDtoMapping(String from, String to) throws JsonProcessingException {
    String fromJson = readFileAsString(testFilesBasePath + from);
    String toJson = readFileAsString(testFilesBasePath + to);

    SecretRequest secretRequest = objectMapper.readValue(fromJson, SecretRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(secretRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    violations = validator.validate(secretDTOV2);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String secretDtoJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(secretDTOV2);
    JSONAssert.assertEquals(toJson, secretDtoJson);
  }

  private void testResponseMapping(String from, String to) throws JsonProcessingException {
    String fromJson = readFileAsString(testFilesBasePath + from);
    String toJson = readFileAsString(testFilesBasePath + to);

    SecretResponseWrapper secretResponseWrapper = objectMapper.readValue(fromJson, SecretResponseWrapper.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(secretResponseWrapper);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    SecretResponse secretResponse = secretApiUtils.toSecretResponse(secretResponseWrapper);
    violations = validator.validate(secretResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String secretResponseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(secretResponse);
    JSONAssert.assertEquals(toJson, secretResponseJson);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testSecretDtoMapping() throws JsonProcessingException {
    testSecretDtoMapping("kerberos-tgt-key-tab-file-1.json", "kerberos-tgt-key-tab-file-2.json");
    testSecretDtoMapping("kerberos-tgt-password-1.json", "kerberos-tgt-password-2.json");
    testSecretDtoMapping("secret-file-1.json", "secret-file-2.json");
    testSecretDtoMapping("secret-text-1.json", "secret-text-2.json");
    testSecretDtoMapping("ssh-key-path-1.json", "ssh-key-path-2.json");
    testSecretDtoMapping("ssh-key-reference-1.json", "ssh-key-reference-2.json");
    testSecretDtoMapping("ssh-password-1.json", "ssh-password-2.json");
    testSecretDtoMapping("winrm-ntlm-1.json", "winrm-ntlm-2.json");
    testSecretDtoMapping("winrm-tgt-key-tab-file-1.json", "winrm-tgt-key-tab-file-2.json");
    testSecretDtoMapping("winrm-tgt-password-1.json", "winrm-tgt-password-2.json");
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testResponseMapping() throws JsonProcessingException {
    testResponseMapping("kerberos-tgt-key-tab-file-3.json", "kerberos-tgt-key-tab-file-4.json");
    testResponseMapping("kerberos-tgt-password-3.json", "kerberos-tgt-password-4.json");
    testResponseMapping("secret-file-3.json", "secret-file-4.json");
    testResponseMapping("secret-text-3.json", "secret-text-4.json");
    testResponseMapping("ssh-key-path-3.json", "ssh-key-path-4.json");
    testResponseMapping("ssh-key-reference-3.json", "ssh-key-reference-4.json");
    testResponseMapping("ssh-password-3.json", "ssh-password-4.json");
    testResponseMapping("winrm-ntlm-3.json", "winrm-ntlm-4.json");
    testResponseMapping("winrm-tgt-key-tab-file-3.json", "winrm-tgt-key-tab-file-4.json");
    testResponseMapping("winrm-tgt-password-3.json", "winrm-tgt-password-4.json");
  }

  public static String readFileAsString(String file) {
    try {
      return new String(Files.readAllBytes(Paths.get(file)));
    } catch (Exception ex) {
      Assert.fail("Failed reading the json from " + file + " with error " + ex.getMessage());
      return "";
    }
  }
}