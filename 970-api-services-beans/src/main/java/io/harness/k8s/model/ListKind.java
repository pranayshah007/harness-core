/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

public enum ListKind {
  List(null),
  NamespaceList(Kind.Namespace),
  ServiceList(Kind.Service),
  DeploymentList(Kind.Deployment),
  RoleBindingList(Kind.RoleBinding),
  ClusterRoleBindingList(Kind.ClusterRoleBinding),
  ConfigMapList(Kind.ConfigMap),
  SecretList(Kind.Secret);

  private final Kind itemKind;

  ListKind(Kind itemKind) {
    this.itemKind = itemKind;
  }

  public Kind getItemKind() {
    return itemKind;
  }
}
