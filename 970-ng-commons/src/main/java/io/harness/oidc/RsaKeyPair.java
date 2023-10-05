/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@OwnedBy(PL)
public class RsaKeyPair {
  RSAPublicKey publicKey;
  RSAPrivateKey privateKey;
}
