/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

// This functor is only to assure compatability between all  SecretManagerFunctors
public interface NgSecretManagerFunctorInterface {
  String FUNCTOR_NAME = "ngSecretManager";
  Object obtain(String secretIdentifier, int token);
}
