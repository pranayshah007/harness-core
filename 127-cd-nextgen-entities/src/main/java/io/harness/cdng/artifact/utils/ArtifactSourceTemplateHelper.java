/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactSourceConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ArtifactSourceTemplateHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;

  public List<EntityDetailProtoDTO> getReferencesFromYaml(
      String accountId, String orgId, String projectId, String entityYaml) {
    List<String> qualifiedNameList = List.of("artifactSource");
    EntityReferenceExtractorVisitor visitor =
        simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(accountId, orgId, projectId, qualifiedNameList);
    ArtifactSourceConfig artifactSourceConfig = toArtifactSourceConfig(entityYaml);
    visitor.walkElementTree(artifactSourceConfig);
    return new ArrayList<>(visitor.getEntityReferenceSet());
  }

  private ArtifactSourceConfig toArtifactSourceConfig(String entityYaml) {
    if (isNotEmpty(entityYaml)) {
      try {
        YamlField yaml = YamlUtils.getTopRootFieldInYaml(entityYaml);
        String artifactSourceYaml = YamlUtils.writeYamlString(yaml);
        return YamlUtils.read(artifactSourceYaml, ArtifactSourceConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot process artifact source config due to: " + e.getMessage(), e);
      }
    }
    return ArtifactSourceConfig.builder().build();
  }
}
