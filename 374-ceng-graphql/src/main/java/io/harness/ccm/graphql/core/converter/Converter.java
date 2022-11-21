/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.converter;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This is a generic converter provides the common way of bidirectional conversion between two types of objects.
 * for e.g. this can be used to convert the entity object to DTOs and vice-versa.
 * @param <A>
 * @param <B>
 */
public class Converter<A, B> {
  private final Function<A, B> fromDto;
  private final Function<B, A> fromEntity;

  public Converter(final Function<A, B> fromDto, final Function<B, A> fromEntity) {
    this.fromDto = fromDto;
    this.fromEntity = fromEntity;
  }

  public final B convertFromDto(final A dto) {
    return fromDto.apply(dto);
  }

  public final A convertFromEntity(final B entity) {
    return fromEntity.apply(entity);
  }

  public final List<B> createFromDtos(final Collection<A> dtos) {
    return dtos.stream().map(this::convertFromDto).collect(Collectors.toList());
  }

  public final List<A> createFromEntities(final Collection<B> entities) {
    return entities.stream().map(this::convertFromEntity).collect(Collectors.toList());
  }
}
