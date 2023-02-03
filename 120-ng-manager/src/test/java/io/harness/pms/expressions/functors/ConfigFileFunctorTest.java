/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.common.EntityTypeConstants.FILES;
import static io.harness.common.EntityTypeConstants.SECRETS;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFileFunctorTest extends CategoryTest {
  private static final String SECRET_REF = "secretRef";
  private static final Long EXPRESSION_FUNCTOR_TOKEN = 1L;
  private static final String FILE_REF_PATH = "/folder1/filename";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String FILENAME = "filename";
  private static final String FILE_CONTENT = "file content";

  @Mock private FileStoreService fileStoreService;

  @InjectMocks private ConfigFileFunctor configFileFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidArgs() {
    assertThatThrownBy(() -> configFileFunctor.get(Ambiance.newBuilder().build(), FUNCTOR_STRING_METHOD_NAME))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Invalid number of config file functor arguments: [getAsString]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidSecretTypeFunctorMethod() {
    assertThatThrownBy(() -> configFileFunctor.get(Ambiance.newBuilder().build(), "obtainSecret", SECRET_REF))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported config secret file functor method: obtainSecret, ref: secretRef");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidFileTypeFunctorMethod() {
    assertThatThrownBy(() -> configFileFunctor.get(Ambiance.newBuilder().build(), "obtainSecret", FILE_REF_PATH))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported config file functor method: obtainSecret, ref: /folder1/filename");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    when(fileStoreService.getWithChildrenByPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_REF_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));

    String fileContent = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, FILE_REF_PATH);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsBase64() {
    when(fileStoreService.getWithChildrenByPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_REF_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));

    String base64FileContent = (String) configFileFunctor.get(getAmbiance(), FUNCTOR_BASE64_METHOD_NAME, FILE_REF_PATH);

    assertThat(base64FileContent).isEqualTo("ZmlsZSBjb250ZW50");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsString() {
    Ambiance ambiance = mock(Ambiance.class);
    when(ambiance.getExpressionFunctorToken()).thenReturn(EXPRESSION_FUNCTOR_TOKEN);

    String secretExpression = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, SECRET_REF);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"secretRef\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsBase64() {
    Ambiance ambiance = mock(Ambiance.class);
    when(ambiance.getExpressionFunctorToken()).thenReturn(EXPRESSION_FUNCTOR_TOKEN);

    String secretExpression = (String) configFileFunctor.get(ambiance, FUNCTOR_BASE64_METHOD_NAME, SECRET_REF);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsBase64(\"secretRef\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetReferenceType() {
    assertThat(configFileFunctor.getReferenceType("account:/folder")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("account:/folder/file")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("account:/folder/file1.txt")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("org:/folder")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("org:/folder/file")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("org:/folder/file1/txt")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("/folder")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("/folder/file")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("/folder/file1.txt")).isEqualTo(FILES);
    assertThat(configFileFunctor.getReferenceType("/file1.txt")).isEqualTo(FILES);

    assertThat(configFileFunctor.getReferenceType("account.secretRef")).isEqualTo(SECRETS);
    assertThat(configFileFunctor.getReferenceType("org.secretRef")).isEqualTo(SECRETS);
    assertThat(configFileFunctor.getReferenceType(SECRET_REF)).isEqualTo(SECRETS);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .build();
  }

  private FileNodeDTO getFileNodeDTO() {
    return FileNodeDTO.builder().name(FILENAME).path(FILE_REF_PATH).content(FILE_CONTENT).build();
  }
}
