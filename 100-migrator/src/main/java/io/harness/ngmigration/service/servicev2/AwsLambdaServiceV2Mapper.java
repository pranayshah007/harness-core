/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.generateFileIdentifier;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.AwsLambdaServiceSpec;
import io.harness.cdng.service.beans.AwsLambdaServiceSpec.AwsLambdaServiceSpecBuilder;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.LambdaSpecification;
import software.wings.beans.LambdaSpecification.FunctionSpecification;
import software.wings.beans.Service;

import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class AwsLambdaServiceV2Mapper implements ServiceV2Mapper {
  private static final String LAMBDA_REQUEST_FORMAT = "{\n"
      + "   \"FunctionName\": \"%s\",\n"
      + "   \"Handler\": \"%s\",\n"
      + "   \"MemorySize\": %d,\n"
      + "   \"Runtime\": \"%s\",\n"
      + "   \"Timeout\": %d\n"
      + "}";
  @Override
  public ServiceDefinition getServiceDefinition(MigrationContext migrationContext, Service service,
      List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<StartupScriptConfiguration> startupScriptConfigurations) {
    PrimaryArtifact primaryArtifact = getPrimaryArtifactStream(migrationContext.getInputDTO(),
        migrationContext.getEntities(), migrationContext.getGraph(), service, migrationContext.getMigratedEntities());

    AwsLambdaServiceSpecBuilder serviceSpecBuilder = AwsLambdaServiceSpec.builder();

    if (primaryArtifact != null) {
      serviceSpecBuilder.artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build());
    }
    if (EmptyPredicate.isNotEmpty(manifests)) {
      serviceSpecBuilder.manifests(manifests);
    }

    serviceSpecBuilder.variables(MigratorUtility.getServiceVariables(migrationContext, service.getServiceVariables()));
    serviceSpecBuilder.configFiles(configFiles);

    return ServiceDefinition.builder()
        .type(ServiceDefinitionType.AWS_LAMBDA)
        .serviceSpec(serviceSpecBuilder.build())
        .build();
  }

  @Override
  public List<NGYamlFile> getChildYamlFiles(
      MigrationContext migrationContext, Service service, LambdaSpecification lambdaSpecification) {
    if (null == lambdaSpecification || isEmpty(lambdaSpecification.getFunctions())) {
      return null;
    }

    List<NGYamlFile> files = new LinkedList<>();
    lambdaSpecification.getFunctions().forEach(f -> {
      String content = getManifestContent(migrationContext, f);
      files.add(MigratorUtility.getYamlManifestFile(migrationContext.getInputDTO(), content.getBytes(),
          getFileName(
              service.getName(), f.getFunctionName(), migrationContext.getInputDTO().getIdentifierCaseFormat())));
    });
    return files;
  }

  private String getManifestContent(MigrationContext migrationContext, FunctionSpecification specification) {
    String content = String.format(LAMBDA_REQUEST_FORMAT, specification.getFunctionName(), specification.getHandler(),
        specification.getMemorySize(), specification.getRuntime(), specification.getTimeout());
    return (String) MigratorExpressionUtils.render(
        migrationContext, content, migrationContext.getInputDTO().getCustomExpressions());
  }

  @NotNull
  public static String getFileName(String serviceName, String functionName, CaseFormat identifierCaseFormat) {
    String fileName = serviceName + "/" + functionName + "/definition.json";
    return generateFileIdentifier(fileName, identifierCaseFormat);
  }
}
