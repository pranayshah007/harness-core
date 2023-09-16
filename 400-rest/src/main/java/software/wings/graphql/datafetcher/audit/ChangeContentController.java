/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.GraphQLException;
import io.harness.exception.WingsException;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeaderYamlResponse;
import software.wings.audit.AuditHeaderYamlResponse.AuditHeaderYamlResponseBuilder;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.record.EntityYamlRecord;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.audit.QLChangeContent;
import software.wings.graphql.schema.type.audit.QLChangeContentConnection.QLChangeContentConnectionBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotNull;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ChangeContentController {
  @Inject private WingsPersistence wingsPersistence;

  public void populateChangeContent(
      @NotNull String changeSetId, @NotNull String resourceId, QLChangeContentConnectionBuilder connectionBuilder) {
    AuditHeader audit = getAuditHeaderById(changeSetId);
    if (audit == null) {
      return;
    }
    List<EntityAuditRecord> entityAuditRecords = audit.getEntityAuditRecords();
    List<EntityAuditRecord> filteredEntityAuditRecords =
        Optional.ofNullable(entityAuditRecords)
            .orElseGet(Collections::emptyList)
            .stream()
            .filter(entityAuditRecord -> resourceId.equals(entityAuditRecord.getEntityId()))
            .collect(Collectors.toList());
    List<AuditHeaderYamlResponse> yamlList = getYamlsForEntityAuditRecords(changeSetId, filteredEntityAuditRecords);
    if (isNotEmpty(yamlList)) {
      addResponseToConnectionBuilder(yamlList, connectionBuilder);
      connectionBuilder.pageInfo(QLPageInfo.builder().hasMore(Boolean.FALSE).total(1).limit(1).offset(0).build());
    }
  }

  public void populateChangeContent(@NotNull String changeSetId, QLChangeContentConnectionBuilder connectionBuilder,
      QLPageQueryParameters pageQueryParameters) {
    int limit = pageQueryParameters.getLimit();
    if (limit != 0) {
      List<EntityAuditRecord> entityAuditRecordsrecords = getEntityAuditRecordsForChangeSetId(changeSetId);
      if (isEmpty(entityAuditRecordsrecords)) {
        return;
      }
      int offset = pageQueryParameters.getOffset();
      List<AuditHeaderYamlResponse> yamlList =
          getYamlListForChangeSetId(changeSetId, entityAuditRecordsrecords, offset, limit);
      if (isNotEmpty(yamlList)) {
        addResponseToConnectionBuilder(yamlList, connectionBuilder);
        connectionBuilder.pageInfo(QLPageInfo.builder()
                                       .total(entityAuditRecordsrecords.size())
                                       .limit(limit)
                                       .offset(offset)
                                       .hasMore(offset + limit < entityAuditRecordsrecords.size())
                                       .build());
      }
    }
  }

  private void addResponseToConnectionBuilder(
      List<AuditHeaderYamlResponse> yamlList, QLChangeContentConnectionBuilder connectionBuilder) {
    List<QLChangeContent> list = populateChangeSetContent(yamlList);
    if (isNotEmpty(list)) {
      connectionBuilder.nodes(list);
    }
  }

  private List<QLChangeContent> populateChangeSetContent(@NotNull List<AuditHeaderYamlResponse> changeSetContents) {
    return changeSetContents.stream()
        .map(changeSetContent
            -> QLChangeContent.builder()
                   .changeSetId(changeSetContent.getAuditHeaderId())
                   .resourceId(changeSetContent.getEntityId())
                   .oldYaml(changeSetContent.getOldYaml())
                   .oldYamlPath(changeSetContent.getOldYamlPath())
                   .newYaml(changeSetContent.getNewYaml())
                   .newYamlPath(changeSetContent.getNewYamlPath())
                   .build())
        .collect(Collectors.toList());
  }

  /**
   * Fetch all entity audit records for a given changeSetId(auditHeaderId)
   * @param changeSetId
   * @return list of EntityAuditRecord instances
   */
  private List<EntityAuditRecord> getEntityAuditRecordsForChangeSetId(@NotNull String changeSetId) {
    if (isEmpty(changeSetId)) {
      throw new GraphQLException("changeSetId is needed.", WingsException.USER_SRE);
    }

    AuditHeader header = getAuditHeaderById(changeSetId);

    if (header == null) {
      throw new GraphQLException("changeSetId does not exist", WingsException.USER_SRE);
    }

    if (isEmpty(header.getEntityAuditRecords())) {
      return null;
    }

    return header.getEntityAuditRecords();
  }

  /**
   * Fetch paginated response for a given limit and response of yamls for given changeSetId and it's audit records
   * @param changeSetId
   * @param entityAuditRecords
   * @param offset
   * @param limit
   * @return
   */
  private List<AuditHeaderYamlResponse> getYamlListForChangeSetId(
      String changeSetId, List<EntityAuditRecord> entityAuditRecords, int offset, int limit) {
    // fetch entityAuditRecords from offset to offset + limit out of all the entityAuditRecords
    List<EntityAuditRecord> entityAuditRecordsSub = IntStream.range(offset, offset + limit)
                                                        .filter(i -> i < entityAuditRecords.size())
                                                        .mapToObj(i -> entityAuditRecords.get(i))
                                                        .collect(Collectors.toList());

    if (isEmpty(entityAuditRecordsSub)) {
      return null;
    }

    return getYamlsForEntityAuditRecords(changeSetId, entityAuditRecordsSub);
  }

  private AuditHeader getAuditHeaderById(@NotNull String changeSetId) {
    return wingsPersistence.createQuery(AuditHeader.class)
        .filter(AuditHeader.ID_KEY2, changeSetId)
        .project("entityAuditRecords", true)
        .get();
  }

  /**
   * Fetch corresponding yamls for corresponding entityAuditRecords by yamlId
   * @param changeSetId
   * @param entityAuditRecords
   * @return list of AuditHeaderYamlResponse instances
   */
  private List<AuditHeaderYamlResponse> getYamlsForEntityAuditRecords(
      String changeSetId, List<EntityAuditRecord> entityAuditRecords) {
    if (isEmpty(entityAuditRecords)) {
      return null;
    }
    List<AuditHeaderYamlResponse> yamls = new LinkedList<>();

    Set<String> yamlIds = entityAuditRecords.stream()
                              .map(entityAuditRecord -> entityAuditRecord.getEntityOldYamlRecordId())
                              .collect(Collectors.toSet());
    yamlIds.addAll(entityAuditRecords.stream()
                       .map(entityAuditRecord -> entityAuditRecord.getEntityNewYamlRecordId())
                       .collect(Collectors.toSet()));

    if (isNotEmpty(yamlIds)) {
      Query<EntityYamlRecord> query = wingsPersistence.createQuery(EntityYamlRecord.class).field("_id").in(yamlIds);

      Map<String, EntityYamlRecord> yamlIdToAuditHeaderYamlResponse = query.asList().stream().collect(
          Collectors.toMap(entityYamlRecord -> entityYamlRecord.getUuid(), entityYamlRecord -> entityYamlRecord));

      if (isNotEmpty(yamlIdToAuditHeaderYamlResponse)) {
        entityAuditRecords.forEach(entityAuditRecord -> {
          EntityYamlRecord oldYamlRecord =
              yamlIdToAuditHeaderYamlResponse.get(entityAuditRecord.getEntityOldYamlRecordId());
          EntityYamlRecord newYamlRecord =
              yamlIdToAuditHeaderYamlResponse.get(entityAuditRecord.getEntityNewYamlRecordId());
          String oldYamlContent, oldYamlPath, newYamlContent, newYamlPath;
          oldYamlContent = oldYamlPath = newYamlContent = newYamlPath = "";
          if (oldYamlRecord != null) {
            oldYamlContent = oldYamlRecord.getYamlContent();
            oldYamlPath = oldYamlRecord.getYamlPath();
          }
          if (newYamlRecord != null) {
            newYamlContent = newYamlRecord.getYamlContent();
            newYamlPath = newYamlRecord.getYamlPath();
          }
          yamls.add(createAuditHeaderYamlResponse(
              changeSetId, entityAuditRecord.getEntityId(), oldYamlContent, oldYamlPath, newYamlContent, newYamlPath));
        });
      }
    }

    return yamls;
  }

  /**
   * Create an instance of AuditHeaderYamlResponse
   * @param auditHeaderId
   * @param entityId
   * @param oldYaml
   * @param oldYamlPath
   * @param newYaml
   * @param newYamlPath
   * @return an instance of AuditHeaderYamlResponse
   */
  private AuditHeaderYamlResponse createAuditHeaderYamlResponse(
      String auditHeaderId, String entityId, String oldYaml, String oldYamlPath, String newYaml, String newYamlPath) {
    AuditHeaderYamlResponseBuilder builder = AuditHeaderYamlResponse.builder()
                                                 .auditHeaderId(auditHeaderId)
                                                 .entityId(entityId)
                                                 .oldYaml(oldYaml)
                                                 .oldYamlPath(oldYamlPath)
                                                 .newYaml(newYaml)
                                                 .newYamlPath(newYamlPath);

    return builder.build();
  }
}
