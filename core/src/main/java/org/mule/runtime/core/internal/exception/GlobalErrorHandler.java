/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.exception;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.privileged.exception.TemplateOnErrorHandler;

import org.reactivestreams.Publisher;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static org.mule.runtime.api.util.MuleSystemProperties.REUSE_GLOBAL_ERROR_HANDLER_PROPERTY;

public class GlobalErrorHandler extends ErrorHandler {

  public static final boolean REUSE_GLOBAL_ERROR_HANDLER = parseBoolean(getProperty(REUSE_GLOBAL_ERROR_HANDLER_PROPERTY));

  private final AtomicBoolean initialised = new AtomicBoolean(false);
  private final AtomicInteger started = new AtomicInteger(0);
  private final AtomicBoolean disposed = new AtomicBoolean(false);

  @Override
  public Publisher<CoreEvent> apply(Exception exception) {
    throw new IllegalStateException("GlobalErrorHandlers should be used only as template for local ErrorHandlers");
  }

  @Override
  public void initialise() throws InitialisationException {
    if (!REUSE_GLOBAL_ERROR_HANDLER) {
      super.initialise();
      return;
    }

    if (!initialised.getAndSet(true)) {
      setFromGlobalErrorHandler();
      super.initialise();
    }
  }

  @Override
  public void start() throws MuleException {
    if (!REUSE_GLOBAL_ERROR_HANDLER) {
      super.start();
      return;
    }

    if (started.getAndIncrement() == 0) {
      super.start();
    }
  }

  @Override
  public void stop() throws MuleException {
    if (!REUSE_GLOBAL_ERROR_HANDLER) {
      super.stop();
      return;
    }

    if (started.decrementAndGet() == 0) {
      super.stop();
    }
  }

  @Override
  public void dispose() {
    if (!REUSE_GLOBAL_ERROR_HANDLER) {
      super.dispose();
      return;
    }

    if (started.get() == 0 && !disposed.getAndSet(true)) {
      super.dispose();
    }
  }

  private void setFromGlobalErrorHandler() {
    this.getExceptionListeners().stream()
        .filter(exceptionListener -> exceptionListener instanceof TemplateOnErrorHandler)
        .forEach(exceptionListener -> ((TemplateOnErrorHandler) exceptionListener).setFromGlobalErrorHandler(true));
  }

  public ErrorHandler createLocalErrorHandler(ComponentLocation flowLocation) {
    ErrorHandler local = new ErrorHandler();
    local.setName(name);
    local.setExceptionListeners(getExceptionListeners());
    local.setExceptionListenersLocation(flowLocation);
    return local;
  }
}
