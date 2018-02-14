/**
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.Dependency;

import javax.inject.Provider;

/**
 * Base class for InternalFactories that are used by Providers, to handle
 * circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T> implements InternalFactory<T> {
  
  protected final Object source;
  
  ProviderInternalFactory(Object source) {
    this.source = checkNotNull(source, "source");
  }
  
  protected T circularGet(final Provider<? extends T> provider, final Errors errors,
      InternalContext context, final Dependency<?> dependency,
      ProvisionListenerStackCallback<T> provisionCallback)
      throws ErrorsException {    
    final ConstructionContext<T> constructionContext = context.getConstructionContext(this);

    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
        Class<?> expectedType = dependency.getKey().getTypeLiteral().getRawType();
        // TODO: if we can't proxy this object, can we proxy the other object?
        @SuppressWarnings("unchecked")
        T proxyType = (T) constructionContext.createProxy(
            errors, context.getInjectorOptions(), expectedType);
        return proxyType;
    }

    // Optimization: Don't go through the callback stack if no one's listening.
    constructionContext.startConstruction();
    try {
      if (!provisionCallback.hasListeners()) {
        return provision(provider, errors, dependency, constructionContext);
      } else {
        return provisionCallback.provision(errors, context, new ProvisionCallback<T>() {
          public T call() throws ErrorsException {
            return provision(provider, errors, dependency, constructionContext);
          }
        });
      }
    } finally {
      constructionContext.removeCurrentReference();
      constructionContext.finishConstruction();
    }
  }

  /**
   * Provisions a new instance. Subclasses should override this to catch
   * exceptions & rethrow as ErrorsExceptions.
   */
  protected T provision(Provider<? extends T> provider, Errors errors, Dependency<?> dependency,
      ConstructionContext<T> constructionContext) throws ErrorsException {
    T t = errors.checkForNull(provider.get(), source, dependency);
    constructionContext.setProxyDelegates(t);
    return t;
  }
}
