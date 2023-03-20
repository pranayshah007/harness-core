/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.model.Kind.Secret;
import static io.harness.k8s.model.KubernetesResource.redactSecretValues;
import static io.harness.k8s.utils.ObjectYamlUtils.YAML_DOCUMENT_DELIMITER;
import static io.harness.k8s.utils.ObjectYamlUtils.newLineRegex;
import static io.harness.k8s.utils.ObjectYamlUtils.splitYamlFile;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.KubernetesValuesException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.K8sYamlUtils;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.ListKind;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.parser.Parser.ParserException;
import com.esotericsoftware.yamlbeans.tokenizer.Tokenizer.TokenizerException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

@UtilityClass
@OwnedBy(CDP)
public class ManifestHelper {
  public static final String values_filename = "values.yaml";
  public static final String yaml_file_extension = ".yaml";
  public static final String yml_file_extension = ".yml";
  public static final String kustomizeFileNameYaml = "kustomization.yaml";
  public static final String kustomizeFileNameYml = "kustomization.yml";
  public static final String currentReleaseWorkloadExpression = "${k8s.currentReleaseWorkload}";
  public static final String previousReleaseWorkloadExpression = "${k8s.previousReleaseWorkload}";

  private static final String VALUES_EXPRESSION = ".Values";
  public static final int MAX_VALUES_EXPRESSION_RECURSION_DEPTH = 10;

  public static KubernetesResource getKubernetesResourceFromSpec(String spec) {
    Map map = readKubernetesSpecAsMap(spec);
    if (map == null) {
      return null;
    }
    return getKubernetesResource(spec, map);
  }

  public static List<KubernetesResource> getKubernetesResourcesFromSpec(String spec) {
    Map map = readKubernetesSpecAsMap(spec);
    if (map == null) {
      return emptyList();
    }

    ListKind listKind = getListKind(map);
    return listKind != null ? getKubernetesResources(map, listKind)
                            : ImmutableList.of(getKubernetesResource(spec, map));
  }

  private ListKind getListKind(Map map) {
    String kind = getKind(map);
    for (ListKind listKind : ListKind.values()) {
      if (listKind.name().equalsIgnoreCase(kind)) {
        return listKind;
      }
    }
    return null;
  }

  private List<KubernetesResource> getKubernetesResources(Map map, ListKind listKind) {
    org.yaml.snakeyaml.Yaml yaml = K8sYamlUtils.createYamlWithCustomConstructor();
    List<KubernetesResource> resources =
        getItems(map).stream().map(item -> getKubernetesResource(yaml.dump(item), item)).collect(Collectors.toList());

    resources.forEach(resource -> {
      if (listKind.getItemKind() != null
          && !resource.getResourceId().getKind().equalsIgnoreCase(listKind.getItemKind().name())) {
        throw new KubernetesYamlException(
            String.format("Error processing yaml manifest. %s should only contain %s kind", listKind.name(),
                listKind.getItemKind().name()));
      }
    });

    return resources;
  }

  private static Map readKubernetesSpecAsMap(String spec) {
    try {
      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setCodePointLimit(K8sYamlUtils.customLoadSize());
      Yaml yaml = new Yaml(loaderOptions);
      Object o = yaml.load(spec);
      if (o == null) {
        return null;
      }
      if (o instanceof Map) {
        return (Map) o;
      } else {
        throw new KubernetesYamlException("Invalid Yaml. Object is not a map.");
      }
    } catch (YAMLException e) {
      throw new KubernetesYamlException(e.getMessage(), e.getCause());
    }
  }

  private static KubernetesResource getKubernetesResource(String spec, Map map) {
    String kind = getKind(map);
    Map metadata = getMetadata(map);
    String name = getName(metadata);

    String namespace = null;
    if (metadata.containsKey("namespace") && metadata.get("namespace") != null) {
      namespace = metadata.get("namespace").toString();
    }

    return KubernetesResource.builder()
        .resourceId(KubernetesResourceId.builder().kind(kind).name(name).namespace(namespace).build())
        .value(map)
        .spec(spec)
        .build();
  }

  private static String getName(Map metadata) {
    if (!metadata.containsKey("name")) {
      throw new KubernetesYamlException("Error processing yaml manifest. metadata.name not found in spec.");
    }

    if (metadata.get("name") == null) {
      throw new KubernetesYamlException("Error processing yaml manifest. metadata.name is set to null in spec.");
    }

    return metadata.get("name").toString();
  }

  private static Map getMetadata(Map map) {
    if (!map.containsKey("metadata")) {
      throw new KubernetesYamlException("Error processing yaml manifest. metadata not found in spec.");
    }

    if (map.get("metadata") == null) {
      throw new KubernetesYamlException("Error processing yaml manifest. metadata is set to null in spec.");
    }

    return (Map) map.get("metadata");
  }

  private static String getKind(Map map) {
    if (!map.containsKey("kind")) {
      throw new KubernetesYamlException("Error processing yaml manifest. kind not found in spec.");
    }

    if (map.get("kind") == null) {
      throw new KubernetesYamlException("Error processing yaml manifest. kind is set to null in spec.");
    }
    return map.get("kind").toString();
  }

  private static List<Map> getItems(Map metadata) {
    if (!metadata.containsKey("items")) {
      throw new KubernetesYamlException("Error processing yaml manifest. items not found in spec.");
    }

    if (metadata.get("items") == null) {
      throw new KubernetesYamlException("Error processing yaml manifest. items is set to null in spec.");
    }

    return (List<Map>) metadata.get("items");
  }

  public static List<KubernetesResource> processYaml(String yamlString) {
    List<String> specs = splitYamlFile(yamlString);

    List<KubernetesResource> resources = new ArrayList<>();

    for (String spec : specs) {
      List<KubernetesResource> resourcesFromSpec = getKubernetesResourcesFromSpec(spec);
      if (isNotEmpty(resourcesFromSpec)) {
        resources.addAll(resourcesFromSpec);
      }
    }

    return resources;
  }

  public static String toYaml(List<KubernetesResource> resources) {
    StringBuilder stringBuilder = new StringBuilder();

    for (KubernetesResource resource : resources) {
      if (!resource.getSpec().startsWith(YAML_DOCUMENT_DELIMITER)) {
        stringBuilder.append(YAML_DOCUMENT_DELIMITER).append(System.lineSeparator());
      }
      stringBuilder.append(resource.getSpec()).append(System.lineSeparator());
    }

    return stringBuilder.toString();
  }

  public static String toYamlForLogs(List<KubernetesResource> resources) {
    StringBuilder stringBuilder = new StringBuilder();

    for (KubernetesResource resource : resources) {
      String spec = StringUtils.equals(Secret.name(), resource.getResourceId().getKind())
          ? redactSecretValues(resource.getSpec())
          : resource.getSpec();
      if (!spec.startsWith(YAML_DOCUMENT_DELIMITER)) {
        stringBuilder.append(YAML_DOCUMENT_DELIMITER).append(System.lineSeparator());
      }
      stringBuilder.append(spec);
    }

    return stringBuilder.toString();
  }

  private static final Set<String> managedWorkloadKinds =
      ImmutableSet.of("Deployment", "StatefulSet", "DaemonSet", "DeploymentConfig");

  private static final Set<String> allManagedWorkloadKinds =
      ImmutableSet.of("Deployment", "StatefulSet", "DaemonSet", "DeploymentConfig", "Job");

  public static List<KubernetesResource> getWorkloads(List<KubernetesResource> resources) {
    if (isEmpty(resources)) {
      return emptyList();
    }
    return resources.stream()
        .filter(resource -> managedWorkloadKinds.contains(resource.getResourceId().getKind()))
        .filter(resource -> !resource.isDirectApply())
        .collect(Collectors.toList());
  }

  // Maintaining backward compatibility here
  public static List<KubernetesResource> getWorkloadsForCanaryAndBG(List<KubernetesResource> resources) {
    final List<KubernetesResource> resources1 =
        resources.stream()
            .filter(resource
                -> ImmutableSet.of(Kind.Deployment.name(), Kind.DeploymentConfig.name())
                       .contains(resource.getResourceId().getKind()))
            .filter(resource -> !resource.isDirectApply())
            .collect(Collectors.toList());

    if (!resources1.isEmpty()) {
      return resources1;
    }

    return resources.stream()
        .filter(resource -> ImmutableSet.of(Kind.StatefulSet.name()).contains(resource.getResourceId().getKind()))
        .filter(resource -> !resource.isDirectApply())
        .collect(Collectors.toList());
  }

  public static List<KubernetesResource> getEligibleWorkloads(List<KubernetesResource> resources) {
    return resources.stream()
        .filter(resource
            -> ImmutableSet
                   .of(Kind.Deployment.name(), Kind.StatefulSet.name(), Kind.DaemonSet.name(), Kind.Job.name(),
                       Kind.DeploymentConfig.name())
                   .contains(resource.getResourceId().getKind()))
        .filter(resource -> !resource.isDirectApply())
        .collect(Collectors.toList());
  }

  public static List<KubernetesResource> getCustomResourceDefinitionWorkloads(List<KubernetesResource> resources) {
    if (isEmpty(resources)) {
      return emptyList();
    }
    return resources.stream()
        .filter(resource -> !allManagedWorkloadKinds.contains(resource.getResourceId().getKind()))
        .filter(resource -> resource.isManagedWorkload())
        .collect(Collectors.toList());
  }

  public static KubernetesResource getFirstLoadBalancerService(List<KubernetesResource> resources) {
    List<KubernetesResource> loadBalancerServices =
        resources.stream().filter(resource -> resource.isLoadBalancerService()).collect(Collectors.toList());

    if (loadBalancerServices.size() > 0) {
      return loadBalancerServices.get(0);
    }

    return null;
  }

  public static KubernetesResource getManagedWorkload(List<KubernetesResource> resources) {
    List<KubernetesResource> result = getWorkloads(resources);
    if (!result.isEmpty()) {
      return result.get(0);
    }
    return null;
  }

  public static List<KubernetesResource> getServices(List<KubernetesResource> resources) {
    return resources.stream().filter(resource -> resource.isService()).collect(Collectors.toList());
  }

  public static KubernetesResource getPrimaryService(List<KubernetesResource> resources) {
    List<KubernetesResource> filteredResources =
        resources.stream().filter(KubernetesResource::isPrimaryService).collect(Collectors.toList());
    if (filteredResources.size() != 1) {
      if (filteredResources.size() > 1) {
        throw new KubernetesYamlException(
            "More than one service is marked Primary. Please specify only one with annotation "
            + HarnessAnnotations.primaryService);
      }
      return null;
    }
    return filteredResources.get(0);
  }

  public static KubernetesResource getStageService(List<KubernetesResource> resources) {
    List<KubernetesResource> filteredResources =
        resources.stream().filter(KubernetesResource::isStageService).collect(Collectors.toList());
    if (filteredResources.size() != 1) {
      if (filteredResources.size() > 1) {
        throw new KubernetesYamlException(
            "More than one service is marked Stage. Please specify only one with annotation "
            + HarnessAnnotations.stageService);
      }
      return null;
    }
    return filteredResources.get(0);
  }

  public static String getValuesYamlGitFilePath(String filePath, String varFileKey) {
    if (isBlank(filePath)) {
      return varFileKey;
    }

    return normalizeFolderPath(filePath) + varFileKey;
  }

  public static boolean validateValuesFileContents(String valuesFileContent) {
    try {
      if (StringUtils.isBlank(valuesFileContent)) {
        return true;
      }
      YamlReader reader = new YamlReader(valuesFileContent);
      Object o = reader.read();
      if (o == null || o instanceof Map) {
        // noop
        return true;
      } else {
        throw new KubernetesValuesException("Object is not a map.");
      }
    } catch (YamlException e) {
      String message = e.getMessage();
      if (e.getCause() != null) {
        Throwable cause = e.getCause();
        if (cause instanceof ParserException || cause instanceof TokenizerException) {
          String lineSnippet = getYamlLineKey(valuesFileContent, cause.getMessage());
          message += " " + cause.getMessage() + ". @[" + lineSnippet + "]";
        }
      }
      throw new KubernetesValuesException(message, e.getCause());
    }
  }

  public static Map getMapFromValuesFileContent(String valuesFileContent) {
    Map map = null;

    try {
      YamlReader reader = new YamlReader(valuesFileContent);
      Object o = reader.read();
      if (o == null) {
        return map;
      }
      if (o instanceof Map) {
        map = (Map) o;
      } else {
        throw new WingsException("Invalid Yaml. Object is not a map.");
      }
    } catch (YamlException e) {
      String message = e.getMessage();

      if (e.getCause() != null) {
        Throwable cause = e.getCause();
        if (cause instanceof ParserException || cause instanceof TokenizerException) {
          String lineSnippet = getYamlLineKey(valuesFileContent, cause.getMessage());
          message += " " + cause.getMessage() + ". @[" + lineSnippet + "]";
        }
      }

      throw new WingsException(message, e.getCause());
    }

    return map;
  }

  public static Set<String> getValuesExpressionKeysFromMap(Map map, String parentExpression, int recursionDepth) {
    if (recursionDepth == MAX_VALUES_EXPRESSION_RECURSION_DEPTH) {
      throw new WingsException(
          format("Map is too deep. Max levels supported are %d", MAX_VALUES_EXPRESSION_RECURSION_DEPTH),
          WingsException.USER);
    }

    Set<String> result = new HashSet<>();

    for (Object o : map.entrySet()) {
      Entry<String, Object> entry = (Entry<String, Object>) o;
      String expression = parentExpression + "." + entry.getKey();
      Object value = entry.getValue();

      result.add(VALUES_EXPRESSION + expression);
      if (value instanceof Map) {
        result.addAll(getValuesExpressionKeysFromMap((Map) value, expression, recursionDepth + 1));
      }
    }

    return result;
  }

  private static String getYamlLineKey(String valuesFileContent, String errorMessage) {
    try {
      int lineNumber = Integer.parseInt(
          errorMessage.substring(errorMessage.indexOf("Line ") + "Line ".length(), errorMessage.indexOf(',')));
      String line = valuesFileContent.split(newLineRegex)[lineNumber];
      return line.substring(0, line.indexOf(':'));
    } catch (RuntimeException e) {
      return "<>";
    }
  }

  public static String normalizeFolderPath(String folderPath) {
    if (isBlank(folderPath)) {
      return folderPath;
    }

    return folderPath.endsWith("/") ? folderPath : folderPath + "/";
  }
}