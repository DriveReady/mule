/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.profiling;

import org.mule.runtime.api.profiling.ProfilingEventContext;
import org.mule.runtime.api.profiling.ProfilingProducerScope;
import org.mule.runtime.api.profiling.type.context.TransactionProfilingEventContext;
import org.mule.runtime.api.profiling.type.ProfilingEventType;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.internal.profiling.producer.TransactionProfilingDataProducer;
import org.mule.runtime.feature.internal.config.profiling.ProfilingFeatureFlaggingService;

/**
 * A {@link ProfilingDataProducerProvider} that provides {@link TransactionProfilingDataProducer}
 *
 * @since 4.5.0
 */
public class TransactionProfilingDataProducerProvider
    implements ProfilingDataProducerProvider<TransactionProfilingEventContext, CoreEvent> {

  private final DefaultProfilingService profilingService;
  private final ProfilingFeatureFlaggingService featureFlaggingService;
  private final ProfilingEventType<TransactionProfilingEventContext> profilingEventType;

  public TransactionProfilingDataProducerProvider(DefaultProfilingService profilingService,
                                                  ProfilingEventType<TransactionProfilingEventContext> profiliingEventType,
                                                  ProfilingFeatureFlaggingService featureFlaggingService) {
    this.profilingService = profilingService;
    this.profilingEventType = profiliingEventType;
    this.featureFlaggingService = featureFlaggingService;
  }

  @Override
  public <T extends ProfilingEventContext, S> ResettableProfilingDataProducer<T, S> getProfilingDataProducer(ProfilingProducerScope profilingProducerScope) {
    return (ResettableProfilingDataProducer<T, S>) new TransactionProfilingDataProducer(profilingService, profilingEventType,
                                                                                        profilingProducerScope,
                                                                                        featureFlaggingService);
  }
}