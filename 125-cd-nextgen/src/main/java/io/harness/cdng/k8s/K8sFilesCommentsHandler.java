/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.k8s.yaml.YamlUtility;
import io.harness.cdng.manifest.ManifestType;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.Event;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sFilesCommentsHandler {
  public List<String> removeComments(List<String> valuesFiles, String manifestType) {
    if (ManifestType.K8Manifest.equals(manifestType) || ManifestType.HelmChart.equals(manifestType)) {
      /*
        ToDo: handle other manifest types like OpenShift and Kustomize
        else if (ManifestType.OpenshiftTemplate.equals(manifestType)) {}
        else if (ManifestType.Kustomize.equals(manifestType)) {}
      */
      try {
        return removeCommentsFromValuesYamlFiles(valuesFiles);
      } catch (Exception e) {
        // In case we hit any exception, return original files
        log.error("Unable to remove comments, returning files as is", e);
        return valuesFiles;
      }
    }
    // if any other ManifestType - return override files as it is
    return valuesFiles;
  }

  public String matchOrFallback(String valueYaml, String fallbackValueYaml) {
    if (valueYaml.equals(fallbackValueYaml)) {
      return valueYaml;
    }

    return isYamlValueMatch(valueYaml, fallbackValueYaml) ? valueYaml : fallbackValueYaml;
  }

  private List<String> removeCommentsFromValuesYamlFiles(List<String> valuesFiles) {
    List<String> modified = new ArrayList<>(valuesFiles.size());
    for (String file : valuesFiles) {
      modified.add(YamlUtility.removeComments(file));
    }

    return modified;
  }

  private boolean isYamlValueMatch(String left, String right) {
    Yaml yamlParser = new Yaml();

    Iterator<Event> leftIterator = yamlParser.parse(new StringReader(left)).iterator();
    Iterator<Event> rightIterator = yamlParser.parse(new StringReader(right)).iterator();

    Event lastLeftEvent = null;
    Event lastRightEvent = null;

    while (true) {
      Event leftEvent = null;
      Event rightEvent;

      try {
        if (!leftIterator.hasNext()) {
          return !rightIterator.hasNext();
        }

        leftEvent = leftIterator.next();

        if (!rightIterator.hasNext()) {
          log.warn("No more events available for left yaml value. Last know event: [{}]",
              lastRightEvent == null ? "<null>" : lastRightEvent);
          return false;
        }

        rightEvent = rightIterator.next();
      } catch (Exception e) {
        boolean leftFailed = leftEvent == null;
        log.warn("Unable to parse [{}] event, last valid event {}", leftFailed ? "left" : "right",
            leftFailed ? lastLeftEvent : lastRightEvent);
        return false;
      }

      if (!leftEvent.equals(rightEvent)) {
        String rightContext = createDebugContext(right, rightEvent);
        String leftContext = createDebugContext(left, leftEvent);

        log.warn("Detected non matching value. Actual [{}], expected: [{}]. Right [{}], Left: [{}]", rightEvent,
            leftEvent, rightContext, leftContext);
        return false;
      }

      lastLeftEvent = leftEvent;
      lastRightEvent = rightEvent;
    }
  }

  private String createDebugContext(String yaml, Event fromEvent) {
    return String.format("line: %d, column: %d, content: %s", fromEvent.getStartMark().getLine(),
        fromEvent.getEndMark().getColumn(),
        retrieveLineContent(yaml, fromEvent.getStartMark().getIndex(), fromEvent.getStartMark().getLine(),
            fromEvent.getEndMark().getLine()));
  }

  private String retrieveLineContent(String string, int startIndex, int startLine, int endLine) {
    String start = string.substring(startIndex);
    int linesToRetrieve = startLine == endLine || startLine > endLine ? 1 : endLine - startLine + 1;

    return start.lines().limit(linesToRetrieve).collect(Collectors.joining("\n"));
  }
}
