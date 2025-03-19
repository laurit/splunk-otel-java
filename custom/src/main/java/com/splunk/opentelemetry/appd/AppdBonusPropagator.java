/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.splunk.opentelemetry.appd;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

final class AppdBonusPropagator implements TextMapPropagator {

  static final String NAME = "appd-bonus";
  static final String CTX_KEY = "cisco-bonus-ctx";
  static final String CTX_HEADER_ENV = "cisco-ctx-env";
  static final String CTX_HEADER_SERVICE = "cisco-ctx-service";

  static final String CTX_HEADER_ACCT = "cisco-ctx-acct-id";
  static final String CTX_HEADER_APP = "cisco-ctx-app-id";
  static final String CTX_HEADER_BT = "cisco-ctx-bt-id";
  static final String CTX_HEADER_TIER = "cisco-ctx-tier-id";
  private static final List<String> FIELDS =
      Arrays.asList(
          CTX_HEADER_ACCT,
          CTX_HEADER_APP,
          CTX_HEADER_BT,
          CTX_HEADER_TIER,
          CTX_HEADER_ENV,
          CTX_HEADER_SERVICE);

  public static final ContextKey<AppdBonusContext> CONTEXT_KEY = ContextKey.named(CTX_KEY);
  private static final AppdBonusPropagator INSTANCE = new AppdBonusPropagator();

  @Nullable private String serviceName;
  @Nullable private String environmentName;

  private AppdBonusPropagator() {}

  static AppdBonusPropagator getInstance() {
    return INSTANCE;
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
    if (environmentName != null) {
      setter.set(carrier, CTX_HEADER_ENV, environmentName);
    }
    if (serviceName != null) {
      setter.set(carrier, CTX_HEADER_SERVICE, serviceName);
    }
  }

  @Override
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    String account = getter.get(carrier, CTX_HEADER_ACCT);
    String app = getter.get(carrier, CTX_HEADER_APP);
    String bt = getter.get(carrier, CTX_HEADER_BT);
    String tier = getter.get(carrier, CTX_HEADER_TIER);
    AppdBonusContext appdContext = new AppdBonusContext(account, app, bt, tier);
    return context.with(CONTEXT_KEY, appdContext);
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public void setEnvironmentName(String environmentName) {
    this.environmentName = environmentName;
  }
}
