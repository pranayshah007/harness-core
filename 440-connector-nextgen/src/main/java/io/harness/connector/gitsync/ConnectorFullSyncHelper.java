/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.gitsync;

import static io.harness.grpc.utils.StringValueUtils.getStringFromStringValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.helper.ConnectorEntityDetailUtils;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.GitSyncFileConstants;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.DX)
@Slf4j
@Singleton
public class ConnectorFullSyncHelper {
  private final ConnectorService connectorService;
  private final EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;

  @Inject
  public ConnectorFullSyncHelper(@Named("connectorDecoratorService") ConnectorService connectorService,
      EntityDetailRestToProtoMapper entityDetailRestToProtoMapper) {
    this.connectorService = connectorService;
    this.entityDetailRestToProtoMapper = entityDetailRestToProtoMapper;
  }

  public List<FileChange> getAllEntitiesForFullSync(ScopeDetails scope) {
    final List<ConnectorInfoDTO> connectorResponseDtos = getConnectorListForFullSync(scope);
    return connectorResponseDtos.stream()
        .map(connectorInfoDTO
            -> FileChange.newBuilder()
                   .setFilePath(getFilePathForConnectorInFullSync(connectorInfoDTO))
                   .setEntityDetail(
                       entityDetailRestToProtoMapper.createEntityDetailDTO(ConnectorEntityDetailUtils.getEntityDetail(
                           connectorInfoDTO, scope.getEntityScope().getAccountId())))
                   .build())
        .collect(Collectors.toList());
  }

  @NotNull
  private String getFilePathForConnectorInFullSync(ConnectorInfoDTO connectorInfoDTO) {
    return getConnectorBaseFilePath() + "/" + connectorInfoDTO.getIdentifier() + GitSyncFileConstants.YAML_EXTENSION;
  }

  @NotNull
  private String getConnectorBaseFilePath() {
    return "connectors";
  }

  private List<ConnectorInfoDTO> getConnectorListForFullSync(ScopeDetails scope) {
    // todo(abhinav): do pagination
    final EntityScopeInfo entityScope = scope.getEntityScope();
    final Page<ConnectorResponseDTO> connectorResponseDtos = connectorService.list(entityScope.getAccountId(),
        excludeSecretManagerConnectorForFullSyncInFilter(), getStringFromStringValue(entityScope.getOrgId()),
        getStringFromStringValue(entityScope.getProjectId()), null, null, false, false,
        PageUtils.getPageRequest(0, 1000, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString())));
    return connectorResponseDtos.get()
        .filter(connectorResponseDTO -> connectorResponseDTO.getGitDetails().getFilePath() == null)
        .map(ConnectorResponseDTO::getConnector)
        .collect(Collectors.toList());
  }

  private ConnectorFilterPropertiesDTO excludeSecretManagerConnectorForFullSyncInFilter() {
    return ConnectorFilterPropertiesDTO.builder()
        .categories(Arrays.stream(ConnectorCategory.values())
                        .filter(connectorCategory -> connectorCategory != ConnectorCategory.SECRET_MANAGER)
                        .collect(Collectors.toList()))
        .build();
  }
}
