/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GuiceDependenciesCalculator {
  public Node findCommonParent(Node node1, Node node2) {
    if (node1 == null || node2 == null) {
      return null;
    } else {
      if (node1.current == node2.current) {
        return node1;
      } else if (node1.height > node2.height) {
        return findCommonParent(node1.parent, node2);
      } else {
        return findCommonParent(node1, node2.parent);
      }
    }
  }

  public DependencyGraphStats dependencyGraphStats(Object rootService) throws IllegalAccessException {
    Set<Object> visited = new HashSet<>();
    List<Edge> cycles = new ArrayList<>();
    Set<Class> visitedClasses = new HashSet<>();
    Node rootNode = dependencyGraphStats(Node.builder().current(rootService).build(), visited, visitedClasses, cycles);
    return DependencyGraphStats.builder().rootNode(rootNode).cycles(cycles).build();
  }

  private Node dependencyGraphStats(Node root, Set<Object> visited, Set<Class> visitedClasses, List<Edge> cycles)
      throws IllegalAccessException {
    if (root == null || root.current == null) {
      log.info("Leaf object - {}", root);
      return null;
    } else {
      if (visited.contains(root.current)) {
        log.info("Cycle - {}", root);
        if (root.current.getClass().getPackage().getName().startsWith("io.harness.cvng")
            && visitedClasses.contains(root.current.getClass())) {
          cycles.add(Edge.builder().from(root.parent).to(root).build());
        }
      } else {
        visited.add(root.current);
        visitedClasses.add(root.current.getClass());
        Set<Node> children = new TreeSet<>((n1, n2) -> n2.count - n1.count);
        root.count = 1;
        for (Field f : root.current.getClass().getDeclaredFields()) {
          f.setAccessible(true);
          if (f.getAnnotation(Inject.class) != null || f.getAnnotation(javax.inject.Inject.class) != null) {
            exploreObject(root, visited, visitedClasses, children, f.get(root.current), cycles);
          }
        }
        if (root.current instanceof Map) {
          Map<Object, Object> map = (Map<Object, Object>) root.current;
          for (Map.Entry<?, ?> entry : map.entrySet()) {
            exploreObject(root, visited, visitedClasses, children, entry.getValue(), cycles);
          }
        }
        if (root.current instanceof Set) {
          Set<?> set = (Set<Object>) root.current;
          for (Object object : set) {
            exploreObject(root, visited, visitedClasses, children, object, cycles);
          }
        }
        root.setChildren(children);
        visitedClasses.remove(root.current.getClass());
      }
      return root;
    }
  }

  private void exploreObject(Node root, Set<Object> visited, Set<Class> visitedClasses, Set<Node> children,
      Object injectedField, List<Edge> cycles) throws IllegalAccessException {
    Node node = dependencyGraphStats(Node.builder().current(injectedField).parent(root).height(root.height + 1).build(),
        visited, visitedClasses, cycles);
    if (node != null) {
      root.count += node.count;
    }
    children.add(node);
  }
  @Value
  @Builder
  public static class DependencyGraphStats {
    Node rootNode;
    List<Edge> cycles;
  }

  @Data
  @Builder
  public static class Node {
    Node parent;
    Object current;
    int height;
    int count;
    Set<Node> children;
    @Override
    public String toString() {
      return current.toString() + "," + count;
    }
    @Override
    public int hashCode() {
      return current.hashCode();
    }
  }
  @Value
  @Builder
  public static class Edge {
    Node from;
    Node to;
  }
}
