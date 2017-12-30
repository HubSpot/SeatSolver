package com.hubspot.seatsolver.utils;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import org.immutables.value.Value.Style;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface PairIF<T1, T2> {
  @Parameter T1 first();
  @Parameter T2 second();
}
