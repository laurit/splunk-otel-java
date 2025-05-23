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

package com.splunk.opentelemetry.instrumentation.nocode;

import com.splunk.opentelemetry.javaagent.bootstrap.nocode.NocodeRules;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

class NocodeSpanNameExtractor implements SpanNameExtractor<NocodeMethodInvocation> {
  private final SpanNameExtractor<ClassAndMethod> defaultNamer;

  public NocodeSpanNameExtractor() {
    this.defaultNamer = CodeSpanNameExtractor.create(ClassAndMethod.codeAttributesGetter());
  }

  @Override
  public String extract(NocodeMethodInvocation mi) {
    NocodeRules.Rule rule = mi.getRule();
    if (rule != null && rule.getSpanName() != null) {
      Object name = mi.evaluate(rule.getSpanName());
      if (name != null) {
        return name.toString();
      }
    }
    return defaultNamer.extract(mi.getClassAndMethod());
  }
}
