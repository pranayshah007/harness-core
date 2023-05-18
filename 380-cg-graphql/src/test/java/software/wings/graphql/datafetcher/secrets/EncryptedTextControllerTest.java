/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secrets;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.WingsBaseTest;
import software.wings.graphql.schema.mutation.secrets.input.QLCreateSecretInput;
import software.wings.graphql.schema.type.secrets.QLEncryptedTextInput;
import software.wings.graphql.schema.type.secrets.QLEncryptedTextUpdate;
import software.wings.graphql.schema.type.secrets.QLSecretType;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/*
 *  This test only checks the creation/updation of the secret reference part
 *  we have checked the CRUD of value part in graphql tests.
 */
public class EncryptedTextControllerTest extends WingsBaseTest {
  @Inject @InjectMocks EncryptedTextController encryptedTextController;
  @Mock SecretManager secretManager;
  private String accountId = "accountId";
  private String secretManagerId = "secretManagerId";
  private String secretReference = "secretReference";
  private String secretName = "testSecret";
  private String secretId = "secretId";
  private String newSecretReference = "newSecretReference";

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testCreatingEncryptedTextWithReference() {
    QLEncryptedTextInput encryptedText = QLEncryptedTextInput.builder()
                                             .name(secretName)
                                             .secretManagerId(secretManagerId)
                                             .secretReference(secretReference)
                                             .build();
    QLCreateSecretInput createSecretInput =
        QLCreateSecretInput.builder().secretType(QLSecretType.ENCRYPTED_TEXT).encryptedText(encryptedText).build();
    encryptedTextController.createEncryptedText(createSecretInput, accountId);
    SecretText secretText = SecretText.builder().name(secretName).kmsId(secretManagerId).path(secretReference).build();
    verify(secretManager, times(1)).saveSecretText(eq(accountId), eq(secretText), eq(true));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testUpdatingReferenceWithReference() {
    // Case when we had reference earlier
    EncryptedData existingSecretValue = EncryptedData.builder().path(secretReference).name(secretName).build();
    when(secretManager.getSecretById(accountId, secretId)).thenReturn(existingSecretValue);
    QLEncryptedTextUpdate createSecretInput = QLEncryptedTextUpdate.builder()
                                                  .name(RequestField.absent())
                                                  .value(RequestField.absent())
                                                  .usageScope(RequestField.absent())
                                                  .secretReference(RequestField.ofNullable(newSecretReference))
                                                  .build();
    encryptedTextController.updateEncryptedText(createSecretInput, secretId, accountId);
    SecretText secretText = SecretText.builder().name(secretName).path(newSecretReference).build();
    verify(secretManager, times(1)).updateSecretText(eq(accountId), eq(secretId), eq(secretText), eq(true));
  }
}
