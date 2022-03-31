/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.profiling.consumer.tracing.span.builder;

import static java.util.concurrent.TimeUnit.MINUTES;

import static org.mule.runtime.core.internal.profiling.consumer.tracing.span.builder.ComponentSpanIdentifier.componentSpanIdentifierFrom;
import static org.mule.runtime.core.internal.profiling.consumer.tracing.span.builder.FlowSpanBuilder.getFlowSpan;
import static org.mule.runtime.core.internal.profiling.consumer.tracing.span.builder.FlowSpanIdentifier.flowSpanIdentifierFrom;

import static com.github.benmanes.caffeine.cache.Caffeine.newBuilder;

import org.mule.runtime.api.profiling.tracing.Span;
import org.mule.runtime.api.profiling.tracing.SpanIdentifier;

import java.util.ArrayList;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * A {@link SpanBuilder} that creates {@link Span} for executable components within a mule app flow.
 *
 * @since 4.5.0
 */
public class ComponentSpanBuilder extends SpanBuilder {

  // TODO: a removal listener has to be added to end the span in case it is not closed by the runtime.
  private static Cache<SpanIdentifier, Span> cache = newBuilder().weakValues().expireAfterAccess(60, MINUTES).build();

  public static Span getComponentSpanFromIdentifier(ComponentSpanIdentifier identifier) {
    return cache.getIfPresent(identifier);
  }

  public static ComponentSpanBuilder builder() {
    return new ComponentSpanBuilder();
  }


  @Override
  protected List<SpanIdentifier> getLinkedSpans() {
    return new ArrayList<>();
  }

  @Override
  protected Span getParent() {
    verifyBuilderParameters();

    return getFlowSpan(getParentIdentifier());
  }

  private FlowSpanIdentifier getParentIdentifier() {

    verifyBuilderParameters();
    return flowSpanIdentifierFrom(artifactId, location.getRootContainerName(), correlationId);
  }


  private void verifyBuilderParameters() {
    if (location == null) {
      throw new IllegalStateException("No location found for the span");
    }

    if (artifactId == null) {
      throw new IllegalStateException("No artifact id found for the span");
    }

    if (correlationId == null) {
      throw new IllegalStateException("No correlationId found for the span");
    }
  }

  @Override
  protected SpanIdentifier getSpanIdentifer() {
    return componentSpanIdentifierFrom(artifactId, location, correlationId);
  }

  @Override
  protected String getSpanName() {
    return componentSpanIdentifierFrom(artifactId, location, correlationId).getId();
  }

}
