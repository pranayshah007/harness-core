/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.vault;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;

/**
 * The absolute path format is (it has to started with a '/'):
 *  /foo/bar/SampleSecret#MyKeyName
 */

@OwnedBy(PL)
public interface VaultRestClient {
  boolean writeSecret(String authToken, String namespace, String secretEngine, String fullPath, String value)
      throws IOException;

  boolean deleteSecret(String authToken, String namespace, String secretEngine, String fullPath) throws IOException;

  boolean deleteSecretPermanentely(String authToken, String secretEngine, String fullPath) throws IOException;

  String readSecret(String authToken, String namespace, String secretEngine, String fullPath) throws IOException;

  VaultSecretMetadata readSecretMetadata(String authToken, String namespace, String secretEngine, String fullPath)
      throws IOException;
}
