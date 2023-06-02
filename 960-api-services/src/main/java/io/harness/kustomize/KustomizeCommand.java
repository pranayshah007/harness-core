/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize;

public enum KustomizeCommand {
  /*
   Going forward if we want to support command flags for other kustomize commands, we can extend this;
   currently we support only kustomize build cmd
   */
  BUILD;
}
