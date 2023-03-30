/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.settings.mappers.BackstagePermissionsMapper;
import io.harness.idp.settings.repositories.BackstagePermissionsRepository;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BackstagePermissionsServiceImpl implements BackstagePermissionsService {
  private static final String SETTINGS_CONFIG = "settings-config";
  private static final String PERMISSIONS = "PERMISSIONS";
  private static final String USERGROUP = "USERGROUP";
  private BackstagePermissionsRepository backstagePermissionsRepository;
  private K8sClient k8sClient;
  private NamespaceService namespaceService;

  @Override
  public Optional<BackstagePermissions> findByAccountIdentifier(String accountIdentifier) {
    Optional<BackstagePermissionsEntity> permissions =
        backstagePermissionsRepository.findByAccountIdentifier(accountIdentifier);
    return permissions.map(BackstagePermissionsMapper::toDTO);
  }

  @Override
  public BackstagePermissions updatePermissions(BackstagePermissions backstagePermissions, String accountIdentifier) {
    updateConfigMap(backstagePermissions, accountIdentifier);
    BackstagePermissionsEntity permissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, accountIdentifier);
    return BackstagePermissionsMapper.toDTO(backstagePermissionsRepository.update(permissionsEntity));
  }

  @Override
  public BackstagePermissions createPermissions(BackstagePermissions backstagePermissions, String accountIdentifier) {
    updateConfigMap(backstagePermissions, accountIdentifier);
    BackstagePermissionsEntity permissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, accountIdentifier);
    return BackstagePermissionsMapper.toDTO(backstagePermissionsRepository.save(permissionsEntity));
  }

  private void updateConfigMap(BackstagePermissions backstagePermissions, String accountIdentifier) {
    List<String> permissions = backstagePermissions.getPermissions();
    String configPermissions = String.join(",", permissions);
    Map<String, String> data = new HashMap<>();
    data.put(PERMISSIONS, configPermissions);
    data.put(USERGROUP, backstagePermissions.getUserGroup());
    k8sClient.updateConfigMapData(getNamespaceForAccount(accountIdentifier), SETTINGS_CONFIG, data, true);
  }
  private String getNamespaceForAccount(String accountIdentifier) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    return namespaceInfo.getNamespace();
  }
}
