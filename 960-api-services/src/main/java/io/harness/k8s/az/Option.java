/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.az;

public enum Option {
  clientId {
    @Override
    public String toString() {
      return "-u";
    }
  },
  password {
    @Override
    public String toString() {
      return "-p";
    }
  },
  cert {
    @Override
    public String toString() {
      return "-p";
    }
  },
  tenantId {
    @Override
    public String toString() {
      return "--tenant";
    }
  },
  username {
    @Override
    public String toString() {
      return "--username";
    }
  }
}
