/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data.structure;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Created by rsingh on 6/1/18.
 */

@UtilityClass
public class CollectionUtils {
  public static boolean isEqualCollection(Collection a, Collection b) {
    if (a == null && b != null) {
      return false;
    }

    if (a != null && b == null) {
      return false;
    }

    if (a == null || b == null) {
      return true;
    }

    if (a.size() != b.size()) {
      return false;
    }

    return a.containsAll(b) && b.containsAll(a);
  }

  /**
   * It goes over the
   * @param keyExtractor
   * @param <T>
   * @return
   */
  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  public static <T> List<T> emptyIfNull(List<T> collection) {
    return collection == null ? new ArrayList<>() : collection;
  }

  public static <T> Set<T> emptyIfNull(Set<T> collection) {
    return collection == null ? new HashSet<>() : collection;
  }

  public static <K, V> Map<K, V> emptyIfNull(Map<K, V> collection) {
    return collection == null ? new HashMap<>() : collection;
  }

  public static <T> List<T> nullIfEmpty(List<T> collection) {
    return collection == null || collection.isEmpty() ? null : collection;
  }

  public static <K, V> Map<K, V> nullIfEmptyMap(Map<K, V> map) {
    return map == null || map.isEmpty() ? null : map;
  }

  public static Set<String> trimmedLowercaseSet(Collection<String> strings) {
    if (strings == null) {
      return null;
    }
    return strings.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(EmptyPredicate::isNotEmpty)
        .map(String::toLowerCase)
        .collect(toSet());
  }

  /**
   * Return true if an element matching the predicate is present
   * @param collection
   * @param predicate
   * @param <T>
   * @return
   */
  public static <T> boolean isPresent(List<T> collection, Predicate<T> predicate) {
    if (collection == null) {
      return false;
    }
    return filterAndGetFirst(collection, predicate).isPresent();
  }

  /**
   * Returns the index in the list for the first match for the predicate
   * @param collection
   * @param predicate
   * @param <T>
   * @return
   */
  public static <T> int fetchIndex(List<T> collection, Predicate<T> predicate) {
    return filterAndGetFirst(collection, predicate).map(collection::indexOf).orElse(-1);
  }

  /**
   * Return Optional for element matching the Predicate
   *
   * @param collection List of Objects
   * @return Optional
   */
  public static <T> Optional<T> filterAndGetFirst(List<T> collection, Predicate<T> predicate) {
    return collection.stream().filter(predicate).findFirst();
  }

  public <T> T overrideOperator(T left, T right) {
    return right;
  }

  /**
   * Null safe way of getting a stream for a collection
   *
   * @param collection collection to obtain stream over
   * @return stream over the collection if not null, else Stream.empty()
   */
  public static <T> Stream<T> collectionToStream(Collection<T> collection) {
    return Optional.ofNullable(collection).map(Collection::stream).orElse(Stream.empty());
  }
}
