/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.HelmCommandFlagConstants;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public interface NgManifestService {
  List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList, CaseFormat identifierCaseFormat);

  default List<HelmManifestCommandFlag> getCommandFlags(ApplicationManifest applicationManifest) {
    if (applicationManifest.getHelmCommandFlag() == null
        || EmptyPredicate.isEmpty(applicationManifest.getHelmCommandFlag().getValueMap())) {
      return null;
    }
    return applicationManifest.getHelmCommandFlag()
        .getValueMap()
        .entrySet()
        .stream()
        .map(entry
            -> HelmManifestCommandFlag.builder()
                   .commandType(mapHelmCommandFlagType(entry.getKey()))
                   .flag(ParameterField.createValueField(entry.getValue()))
                   .build())
        .collect(Collectors.toList());
  }

  default HelmCommandFlagType mapHelmCommandFlagType(HelmCommandFlagConstants.HelmSubCommand key) {
    switch (key) {
      case LIST:
        return HelmCommandFlagType.List;
      case PULL:
        return HelmCommandFlagType.Pull;
      case FETCH:
        return HelmCommandFlagType.Fetch;
      case DELETE:
        return HelmCommandFlagType.Delete;
      case HISTORY:
        return HelmCommandFlagType.History;
      case INSTALL:
        return HelmCommandFlagType.Install;
      case UPGRADE:
        return HelmCommandFlagType.Upgrade;
      case REPO_ADD:
        return HelmCommandFlagType.Add;
      case REPO_UPDATE:
        return HelmCommandFlagType.Update;
      case ROLLBACK:
        return HelmCommandFlagType.Rollback;
      case TEMPLATE:
        return HelmCommandFlagType.Template;
      case UNINSTALL:
        return HelmCommandFlagType.Uninstall;
      default:
        return null;
    }
  }
}
