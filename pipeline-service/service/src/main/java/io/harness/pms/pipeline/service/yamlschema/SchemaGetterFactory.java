/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import io.harness.ModuleType;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.customstage.CustomStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.client.YamlSchemaClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.NonNull;

@Singleton
public class SchemaGetterFactory {
  private final ApprovalYamlSchemaService approvalYamlSchemaService;
  private final CustomStageYamlSchemaService customStageYamlSchemaService;
  private final FeatureFlagYamlService featureFlagYamlService;
  private final YamlSchemaProvider yamlSchemaProvider;
  private Map<String, YamlSchemaClient> yamlSchemaClientMapper;
  private final PmsYamlSchemaHelper pmsYamlSchemaHelper;

  @Inject
  public SchemaGetterFactory(ApprovalYamlSchemaService approvalYamlSchemaService,
      CustomStageYamlSchemaService customStageYamlSchemaService, FeatureFlagYamlService featureFlagYamlService,
      YamlSchemaProvider yamlSchemaProvider, Map<String, YamlSchemaClient> yamlSchemaClientMapper,
      PmsYamlSchemaHelper pmsYamlSchemaHelper) {
    this.approvalYamlSchemaService = approvalYamlSchemaService;
    this.customStageYamlSchemaService = customStageYamlSchemaService;
    this.featureFlagYamlService = featureFlagYamlService;
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaClientMapper = yamlSchemaClientMapper;
    this.pmsYamlSchemaHelper = pmsYamlSchemaHelper;
  }
  public SchemaGetter obtainGetter(@NonNull String accountIdentifier, @NonNull ModuleType moduleType) {
    if (moduleType == ModuleType.PMS || moduleType == ModuleType.CF) {
      return new LocalSchemaGetter(accountIdentifier, moduleType, yamlSchemaProvider, approvalYamlSchemaService,
          customStageYamlSchemaService, featureFlagYamlService, pmsYamlSchemaHelper);
    }

    YamlSchemaClient yamlSchemaClient = yamlSchemaClientMapper.get(moduleType.name().toLowerCase());
    if (yamlSchemaClient == null) {
      throw new IllegalStateException();
    }
    return new RemoteSchemaGetter(yamlSchemaClient, moduleType, accountIdentifier);
  }
}
