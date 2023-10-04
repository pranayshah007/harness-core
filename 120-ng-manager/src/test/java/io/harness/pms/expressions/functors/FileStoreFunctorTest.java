/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.pms.expressions.functors.ConfigFileFunctor.MAX_CONFIG_FILE_SIZE;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class FileStoreFunctorTest extends CategoryTest {
  private static final String FILE_CONTENT = "file content";
  private static final String SCOPED_FILE_PATH = "/folder1/filename";
  public static final String NOT_VALID_FILE_PATH = "not_valid/file/path";
  private static final String BASE64_FILE_CONTENT = "ZmlsZSBjb250ZW50";

  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private FileStoreFunctor fileStoreFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(cdFeatureFlagHelper.isEnabled(anyString(), eq(FeatureName.CDS_NOT_SUPPORT_SECRETS_BASE64_EXPRESSION)))
        .thenReturn(true);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidArgs() {
    assertThatThrownBy(() -> fileStoreFunctor.get(getAmbiance(), FUNCTOR_STRING_METHOD_NAME))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Invalid fileStore functor arguments: [getAsString]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidFilePath() {
    assertThatThrownBy(() -> fileStoreFunctor.get(getAmbiance(), FUNCTOR_STRING_METHOD_NAME, NOT_VALID_FILE_PATH))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("File path not valid: not_valid/file/path");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsString() {
    Ambiance ambiance = getAmbiance();
    when(cdStepHelper.getFileContentAsString(ambiance, SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT);

    String fileContent = (String) fileStoreFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, SCOPED_FILE_PATH);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    when(cdStepHelper.getFileContentAsBase64(ambiance, SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(BASE64_FILE_CONTENT);

    String fileContent = (String) fileStoreFunctor.get(ambiance, FUNCTOR_BASE64_METHOD_NAME, SCOPED_FILE_PATH);

    assertThat(fileContent).isEqualTo(BASE64_FILE_CONTENT);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder().build();
  }
}
