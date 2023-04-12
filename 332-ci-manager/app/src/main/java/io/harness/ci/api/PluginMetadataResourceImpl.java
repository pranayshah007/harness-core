/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.api.PluginMetadataResource;
import io.harness.beans.plugin.api.PluginMetadataResponse;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plugin.PluginMetadataService;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class PluginMetadataResourceImpl implements PluginMetadataResource {
  @Inject PluginMetadataService pluginMetadataService;
  @Override
  public ResponseDTO<PageResponse<PluginMetadataResponse>> list(int page, int size, String searchTerm, String kind) {
    return ResponseDTO.newResponse(pluginMetadataService.listPlugins(searchTerm, kind, page, size));
  }
}
