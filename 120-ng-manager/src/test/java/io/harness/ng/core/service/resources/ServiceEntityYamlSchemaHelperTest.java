/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.rule.Owner;
import io.harness.yaml.validator.YamlSchemaValidator;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceEntityYamlSchemaHelperTest extends CategoryTest {
  @Mock CDFeatureFlagHelper featureFlagHelperService;
  @Mock YamlSchemaValidator yamlSchemaValidator;
  @InjectMocks ServiceEntityYamlSchemaHelper serviceEntityYamlSchemaHelper;
  private final String ACCOUNT_ID = "account_id";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateSchema() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(yamlSchemaValidator.processAndHandleValidationMessage(any(), any(), any())).thenReturn(Collections.emptySet());
    String yaml = "service:\n"
        + "  name: er\n"
        + "  identifier: er\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec: {}\n"
        + "    type: Kubernetes\n";
    serviceEntityYamlSchemaHelper.validateSchema(ACCOUNT_ID, yaml);
    verify(yamlSchemaValidator, times(1)).validateWithDetailedMessage(yaml, EntityType.SERVICE);
    verify(yamlSchemaValidator, times(1)).processAndHandleValidationMessage(any(), any(), anyString());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testThrowExceptionForInvalidServiceEntity() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG))
        .thenReturn(false);
    String yaml = "service:\n"
        + "  name: er\n"
        + "  identifier: er\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      manifestConfigurations:\n"
        + "        primaryManifestRef: manifest\n"
        + "    type: Kubernetes\n";
    assertThatThrownBy(() -> serviceEntityYamlSchemaHelper.validateSchema(ACCOUNT_ID, yaml))
        .hasMessage(
            format("Cannot use primaryManifestRef field. Please contact Harness Support to enable the feature flag: %s",
                FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testServiceEntityWithFFEnabled() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG))
        .thenReturn(true);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    String yaml = "service:\n"
        + "  name: er\n"
        + "  identifier: er\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      manifestConfigurations: \n"
        + "        primaryManifestRef: manifest\n"
        + "    type: Kubernetes\n";
    serviceEntityYamlSchemaHelper.validateSchema(ACCOUNT_ID, "");
    serviceEntityYamlSchemaHelper.validateSchema(ACCOUNT_ID, yaml);
  }
}
