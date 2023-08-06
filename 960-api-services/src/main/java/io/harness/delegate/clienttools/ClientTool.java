/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.clienttools;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Getter
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public enum ClientTool {
  //  CF was not being handled like other client tools before, so will add it in a separate PR.
  //  Installation is more complex than with other tools and need to work with CD team to understand how to do it.
  //  These are only placeholders for now.
  //  CF("cf", "", "./client-tools/helm/", "./cf --version", "", ImmutableList.copyOf(CfVersion.values()),
  //  CfVersion.V7),
  HELM("helm", "public/shared/tools/helm/release/%s/bin/%s/%s/helm", "./client-tools/helm/", "version -c",
      "storage/harness-download/harness-helm/release/%s/bin/%s/%s/helm", ImmutableList.copyOf(HelmVersion.values()),
      HelmVersion.V3_8),
  KUBECTL("kubectl", "public/shared/tools/kubectl/release/%s/bin/%s/%s/kubectl", "./client-tools/kubectl/",
      "version --short --client", "storage/harness-download/kubernetes-release/release/%s/bin/%s/%s/kubectl",
      ImmutableList.copyOf(KubectlVersion.values()), KubectlVersion.V1_19),
  KUSTOMIZE("kustomize", "public/shared/tools/kustomize/release/%s/bin/%s/%s/kustomize", "./client-tools/kustomize/",
      "version --short", "storage/harness-download/harness-kustomize/release/%s/bin/%s/%s/kustomize",
      ImmutableList.copyOf(KustomizeVersion.values()), KustomizeVersion.V4),
  OC("oc", "public/shared/tools/oc/release/%s/bin/%s/%s/oc", "./client-tools/oc/", "version --client",
      "storage/harness-download/harness-oc/release/%s/bin/%s/%s/oc", ImmutableList.copyOf(OcVersion.values()),
      OcVersion.V4_2),
  SCM("scm", "public/shared/tools/scm/release/%s/bin/%s/%s/scm", "./client-tools/scm/", "--version",
      "storage/harness-download/harness-scm/release/%s/bin/%s/%s/scm", ImmutableList.copyOf(ScmVersion.values()),
      ScmVersion.DEFAULT),
  TERRAFORM_CONFIG_INSPECT("terraform-config-inspect",
      "public/shared/tools/terraform-config-inspect/%s/%s/%s/terraform-config-inspect",
      "./client-tools/tf-config-inspect", "",
      "storage/harness-download/harness-terraform-config-inspect/%s/%s/%s/terraform-config-inspect",
      ImmutableList.copyOf(TerraformConfigInspectVersion.values()), TerraformConfigInspectVersion.V1_1),
  GO_TEMPLATE("go-template", "public/shared/tools/go-template/release/%s/bin/%s/%s/go-template",
      "./client-tools/go-template/", "-v",
      "storage/harness-download/snapshot-go-template/release/%s/bin/%s/%s/go-template",
      ImmutableList.copyOf(GoTemplateVersion.values()), GoTemplateVersion.V0_4_4),
  HARNESS_PYWINRM("harness-pywinrm", "public/shared/tools/harness-pywinrm/release/%s/bin/%s/%s/harness-pywinrm",
      "./client-tools/harness-pywinrm/", "-v",
      "storage/harness-download/snapshot-harness-pywinrm/release/%s/bin/%s/%s/harness-pywinrm",
      ImmutableList.copyOf(HarnessPywinrmVersion.values()), HarnessPywinrmVersion.V0_4),
  CHARTMUSEUM("chartmuseum", "public/shared/tools/chartmuseum/release/%s/bin/%s/%s/chartmuseum",
      "./client-tools/chartmuseum/", "-v",
      "storage/harness-download/harness-chartmuseum/release/%s/bin/%s/%s/chartmuseum",
      ImmutableList.copyOf(ChartmuseumVersion.values()), ChartmuseumVersion.V0_12);

  @ToString.Include private final String binaryName;
  private final String cdnPath;
  private final String baseDir;
  private final String validateCommandArgs;
  private final String onPremPath;
  private final List<ClientToolVersion> versions;
  private final ClientToolVersion latestVersion;
}
