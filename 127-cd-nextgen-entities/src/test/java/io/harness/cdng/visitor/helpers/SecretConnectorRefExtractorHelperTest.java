/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.impl.SecretRefInputValidationHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SecretConnectorRefExtractorHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SECRET_REF_ID = "secretRef";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACC_CONNECTOR_REF = "account.connectorRef";

  @Mock private ConnectorService connectorService;
  @Mock private SecretRefInputValidationHelper secretRefInputValidationHelper;

  @InjectMocks private SecretConnectorRefExtractorHelper secretConnectorRefExtractor;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void addSecretReference() {
    final ConnectorConfigDTO configDTO = mock(ConnectorConfigDTO.class);
    final DecryptableEntity decryptableEntity = mock(DecryptableEntity.class);
    final WithConnectorRef withConnectorRef = mock(WithConnectorRef.class);
    final SecretRefData secretRefData = SecretRefData.builder().scope(Scope.PROJECT).identifier(SECRET_REF_ID).build();

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder().connectorConfig(configDTO).build())
                             .build()))
        .when(connectorService)
        .get(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_REF);

    doReturn(Map.of(CONNECTOR_REF, ParameterField.createValueField(CONNECTOR_REF)))
        .when(withConnectorRef)
        .extractConnectorRefs();

    doReturn(List.of(decryptableEntity)).when(configDTO).getDecryptableEntities();
    doReturn(Map.of(SECRET_REF_ID, secretRefData))
        .when(secretRefInputValidationHelper)
        .getDecryptableFieldsData(List.of(decryptableEntity));

    Set<VisitedSecretReference> secretReferences = secretConnectorRefExtractor.addSecretReference(
        withConnectorRef, ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap());
    assertThat(secretReferences).hasSize(1);
    VisitedSecretReference resultSecretRef = secretReferences.iterator().next();
    assertThat(resultSecretRef.getSecretRef()).isNotNull();
    assertThat(resultSecretRef.getReferredBy()).isNotNull();
    assertThat(resultSecretRef.getSecretRef().getIdentifier()).isEqualTo(SECRET_REF_ID);
    assertThat(resultSecretRef.getSecretRef().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(resultSecretRef.getSecretRef().getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(resultSecretRef.getSecretRef().getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(resultSecretRef.getReferredBy().getIdentifierRef().getIdentifier().getValue()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void addSecretReferenceMultiConnector() {
    final ConnectorConfigDTO configDTO = mock(ConnectorConfigDTO.class);
    final DecryptableEntity decryptableEntity = mock(DecryptableEntity.class);
    final WithConnectorRef withConnectorRef = mock(WithConnectorRef.class);
    final SecretRefData secretRefData = SecretRefData.builder().scope(Scope.ORG).identifier(SECRET_REF_ID).build();

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder().connectorConfig(configDTO).build())
                             .build()))
        .when(connectorService)
        .get(ACCOUNT_ID, "", "", CONNECTOR_REF);
    doReturn(Optional.empty()).when(connectorService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_REF);
    doReturn(List.of(decryptableEntity)).when(configDTO).getDecryptableEntities();
    doReturn(Map.of(SECRET_REF_ID, secretRefData))
        .when(secretRefInputValidationHelper)
        .getDecryptableFieldsData(List.of(decryptableEntity));

    doReturn(Map.of(ACC_CONNECTOR_REF, ParameterField.createValueField(ACC_CONNECTOR_REF), CONNECTOR_REF,
                 ParameterField.createValueField(CONNECTOR_REF)))
        .when(withConnectorRef)
        .extractConnectorRefs();

    Set<VisitedSecretReference> secretReferences = secretConnectorRefExtractor.addSecretReference(
        withConnectorRef, ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap());
    assertThat(secretReferences).hasSize(1);
    VisitedSecretReference resultSecretRef = secretReferences.iterator().next();
    assertThat(resultSecretRef.getSecretRef()).isNotNull();
    assertThat(resultSecretRef.getReferredBy()).isNotNull();
    assertThat(resultSecretRef.getSecretRef().getIdentifier()).isEqualTo(SECRET_REF_ID);
    assertThat(resultSecretRef.getSecretRef().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(resultSecretRef.getSecretRef().getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(resultSecretRef.getSecretRef().getProjectIdentifier()).isNull();
    assertThat(resultSecretRef.getReferredBy().getIdentifierRef().getIdentifier().getValue()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void addSecretReferenceEmptySecretsSet() {
    final ConnectorConfigDTO configDTO = mock(ConnectorConfigDTO.class);
    final DecryptableEntity decryptableEntity = mock(DecryptableEntity.class);
    final WithConnectorRef withConnectorRef = mock(WithConnectorRef.class);
    final SecretRefData secretRefData = SecretRefData.builder().scope(Scope.PROJECT).identifier(SECRET_REF_ID).build();

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder().connectorConfig(configDTO).build())
                             .build()))
        .when(connectorService)
        .get(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_REF);

    doReturn(Map.of(CONNECTOR_REF, ParameterField.createValueField(CONNECTOR_REF)))
        .when(withConnectorRef)
        .extractConnectorRefs();

    doReturn(List.of(decryptableEntity)).when(configDTO).getDecryptableEntities();
    doReturn(Map.of()).when(secretRefInputValidationHelper).getDecryptableFieldsData(List.of(decryptableEntity));

    Set<VisitedSecretReference> secretReferences = secretConnectorRefExtractor.addSecretReference(
        withConnectorRef, ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap());
    assertThat(secretReferences).isEmpty();
  }
}