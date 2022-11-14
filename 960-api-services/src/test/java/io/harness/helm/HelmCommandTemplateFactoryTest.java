/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.helm.HelmConstants.V2Commands;
import io.harness.helm.HelmConstants.V3Commands;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HelmCommandTemplateFactoryTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getHelmCommand() {
    testgetHelmVersionTemplate();

    testGetHelmInstallTemplate();

    testgetHelmUpgradeTemplate();

    testgetHelmRollbackTemplate();

    testgetHelmRepoAddTemplate();

    testgetHelmRepoUpdateTemplate();

    testgetHelmRepoListTemplate();

    testgetHelmReleaseHistTemplate();

    testgetHelmListReleaseTemplate();

    testgetHelmDeleteReleaseTemplate();

    testgetHelmRepoSearchTemplate();

    testgetHelmRepoAddChartMeuseumTemplate();

    testgetHelmRepoAddHttpTemplate();

    testgetHelmFetchTemplate();

    testgetHelmRepoRemoveTemplate();

    testgetHelmInitTemplate();

    testgetRenderChartTemplate();

    testgetRenderSpecificChartFileTemplate();
  }

  private void testgetHelmVersionTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.VERSION, null))
        .isEqualTo(V2Commands.HELM_VERSION_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.VERSION, V2))
        .isEqualTo(V2Commands.HELM_VERSION_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.VERSION, V3))
        .isEqualTo(V3Commands.HELM_VERSION_COMMAND_TEMPLATE);
  }

  private void testgetHelmInitTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INIT, null))
        .isEqualTo(V2Commands.HELM_INIT_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INIT, V2))
        .isEqualTo(V2Commands.HELM_INIT_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INIT, V3))
        .isEqualTo(StringUtils.EMPTY);
  }

  private void testgetHelmRepoRemoveTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_REMOVE, null))
        .isEqualTo(V2Commands.HELM_REPO_REMOVE_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_REMOVE, V2))
        .isEqualTo(V2Commands.HELM_REPO_REMOVE_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_REMOVE, V3))
        .isEqualTo(V3Commands.HELM_REPO_REMOVE_COMMAND);
  }

  private void testgetHelmFetchTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH, null))
        .isEqualTo(V2Commands.HELM_FETCH_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH, V2))
        .isEqualTo(V2Commands.HELM_FETCH_COMMAND);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH, V3))
        .isEqualTo(V3Commands.HELM_FETCH_COMMAND);
  }

  private void testgetHelmRepoAddHttpTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_HTTP, null))
        .isEqualTo(V2Commands.HELM_REPO_ADD_COMMAND_FOR_HTTP);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_HTTP, V2))
        .isEqualTo(V2Commands.HELM_REPO_ADD_COMMAND_FOR_HTTP);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_HTTP, V3))
        .isEqualTo(V3Commands.HELM_REPO_ADD_COMMAND_FOR_HTTP);
  }

  private void testgetHelmRepoAddChartMeuseumTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_CHART_MUSEUM, null))
        .isEqualTo(V2Commands.HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_CHART_MUSEUM, V2))
        .isEqualTo(V2Commands.HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_CHART_MUSEUM, V3))
        .isEqualTo(V3Commands.HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM);
  }

  private void testgetHelmRepoSearchTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.SEARCH_REPO, null))
        .isEqualTo(V2Commands.HELM_SEARCH_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.SEARCH_REPO, V2))
        .isEqualTo(V2Commands.HELM_SEARCH_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.SEARCH_REPO, V3))
        .isEqualTo(V3Commands.HELM_SEARCH_COMMAND_TEMPLATE);
  }

  private void testgetHelmDeleteReleaseTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.DELETE_RELEASE, null))
        .isEqualTo(V2Commands.HELM_DELETE_RELEASE_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.DELETE_RELEASE, V2))
        .isEqualTo(V2Commands.HELM_DELETE_RELEASE_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.DELETE_RELEASE, V3))
        .isEqualTo(V3Commands.HELM_DELETE_RELEASE_TEMPLATE);
  }

  private void testgetHelmListReleaseTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.LIST_RELEASE, null))
        .isEqualTo(V2Commands.HELM_LIST_RELEASE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.LIST_RELEASE, V2))
        .isEqualTo(V2Commands.HELM_LIST_RELEASE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.LIST_RELEASE, V3))
        .isEqualTo(V3Commands.HELM_LIST_RELEASE_COMMAND_TEMPLATE);
  }

  private void testgetHelmReleaseHistTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RELEASE_HISTORY, null))
        .isEqualTo(V2Commands.HELM_RELEASE_HIST_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RELEASE_HISTORY, V2))
        .isEqualTo(V2Commands.HELM_RELEASE_HIST_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RELEASE_HISTORY, V3))
        .isEqualTo(V3Commands.HELM_RELEASE_HIST_COMMAND_TEMPLATE);
  }

  private void testgetHelmRepoListTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_LIST, null))
        .isEqualTo(V2Commands.HELM_REPO_LIST_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_LIST, V2))
        .isEqualTo(V2Commands.HELM_REPO_LIST_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_LIST, V3))
        .isEqualTo(V3Commands.HELM_REPO_LIST_COMMAND_TEMPLATE);
  }

  private void testgetHelmRepoUpdateTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, null))
        .isEqualTo(V2Commands.HELM_REPO_UPDATE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, V2))
        .isEqualTo(V2Commands.HELM_REPO_UPDATE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, V3))
        .isEqualTo(V3Commands.HELM_REPO_UPDATE_COMMAND_TEMPLATE);
  }

  private void testgetHelmRepoAddTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD, null))
        .isEqualTo(V2Commands.HELM_ADD_REPO_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD, V2))
        .isEqualTo(V2Commands.HELM_ADD_REPO_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD, V3))
        .isEqualTo(V3Commands.HELM_ADD_REPO_COMMAND_TEMPLATE);
  }

  private void testgetHelmRollbackTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.ROLLBACK, null))
        .isEqualTo(V2Commands.HELM_ROLLBACK_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.ROLLBACK, V2))
        .isEqualTo(V2Commands.HELM_ROLLBACK_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.ROLLBACK, V3))
        .isEqualTo(V3Commands.HELM_ROLLBACK_COMMAND_TEMPLATE);
  }

  private void testgetHelmUpgradeTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.UPGRADE, null))
        .isEqualTo(V2Commands.HELM_UPGRADE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.UPGRADE, V2))
        .isEqualTo(V2Commands.HELM_UPGRADE_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.UPGRADE, V3))
        .isEqualTo(V3Commands.HELM_UPGRADE_COMMAND_TEMPLATE);
  }

  private void testGetHelmInstallTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INSTALL, null))
        .isEqualTo(V2Commands.HELM_INSTALL_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INSTALL, V2))
        .isEqualTo(V2Commands.HELM_INSTALL_COMMAND_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INSTALL, V3))
        .isEqualTo(V3Commands.HELM_INSTALL_COMMAND_TEMPLATE);
  }

  private void testgetRenderSpecificChartFileTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_SPECIFIC_CHART_FILE, null))
        .isEqualTo(V2Commands.HELM_RENDER_SPECIFIC_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_SPECIFIC_CHART_FILE, V2))
        .isEqualTo(V2Commands.HELM_RENDER_SPECIFIC_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_SPECIFIC_CHART_FILE, V3))
        .isEqualTo(V3Commands.HELM_RENDER_SPECIFIC_TEMPLATE);
  }

  private void testgetRenderChartTemplate() {
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_CHART, null))
        .isEqualTo(V2Commands.HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_CHART, V2))
        .isEqualTo(V2Commands.HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE);
    assertThat(HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.RENDER_CHART, V3))
        .isEqualTo(V3Commands.HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE);
  }
}
