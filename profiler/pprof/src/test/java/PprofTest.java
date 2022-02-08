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

import com.google.perftools.profiles.ProfileProto;
import com.google.perftools.profiles.ProfileProto.Label;
import com.google.perftools.profiles.ProfileProto.Profile;
import com.google.perftools.profiles.ProfileProto.Sample;
import com.splunk.opentelemetry.profiler.StackTraceFilter;
import com.splunk.opentelemetry.profiler.TLABProcessor;
import com.splunk.opentelemetry.profiler.ThreadDumpProcessor;
import com.splunk.opentelemetry.profiler.ThreadDumpToStacks;
import com.splunk.opentelemetry.profiler.events.ContextAttached;
import com.splunk.opentelemetry.profiler.util.StackSerializer;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

public class PprofTest {
  private StackSerializer stackSerializer = new StackSerializer();

  @Test
  public void testReadPprof() throws Exception {
    try (InputStream is = new GZIPInputStream(new FileInputStream("/tmp/prof.gz"))) {
      Profile profile = Profile.parseFrom(is);
      System.err.println(profile.getSampleCount());
    }
  }

  @Test
  public void testJfrToPprof() throws Exception {
    ProfilingSession session = new ProfilingSession();

    try (OutputStream textOut =
        new GZIPOutputStream(new FileOutputStream("/tmp/profile-text.gz"))) {
      try (RecordingFile rf =
          new RecordingFile(
              Path.of(
                  "/Users/ltulmin/dev/spring-petclinic-rest/memjfr/otel-profiler-2022-02-07T15_58_44.jfr"))) {
        while (rf.hasMoreEvents()) {
          RecordedEvent recordedEvent = rf.readEvent();
          handleEvent(session, textOut, recordedEvent);
        }
      }
    }

    dump(session.profile.build(), "/tmp/prof.gz");
  }

  private void handleEvent(ProfilingSession session, OutputStream textOut, RecordedEvent event)
      throws Exception {
    LocationTable locationTable = session.locationTable;
    Profile.Builder profile = session.profile;

    String eventName = event.getEventType().getName();
    switch (eventName) {
      case ContextAttached.EVENT_NAME:
        // not handled
        break;
      case ThreadDumpProcessor.EVENT_NAME:
        // in this test we care only about allocation stack traces
        break;
      case TLABProcessor.NEW_TLAB_EVENT_NAME:
      case TLABProcessor.OUTSIDE_TLAB_EVENT_NAME:
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
          return;
        }

        long allocationSize;
        // jdk mission control "TLAB allocations" view seems to use tlabSize as allocation size
        if (event.hasField("tlabSize")) {
          allocationSize = event.getLong("tlabSize");
        } else {
          allocationSize = event.getLong("allocationSize");
        }

        // System.err.println("----");
        Sample.Builder sample = Sample.newBuilder();
        sample.addValue(allocationSize);
        for (RecordedFrame frame : stackTrace.getFrames()) {
          // System.err.println(rf.getMethod().getType().getName() + " " +
          // rf.getMethod().getName());
          RecordedMethod method = frame.getMethod();
          if (method == null) {
            sample.addLocationId(locationTable.get("", "unknown.unknown", -1));
          } else {
            sample.addLocationId(
                locationTable.get(
                    "",
                    method.getType().getName() + "." + method.getName(),
                    frame.getLineNumber()));
          }
        }
        profile.addSample(sample);

        String stackTraceText = buildBody(event, event.getStackTrace()) + "\n";
        textOut.write(stackTraceText.getBytes(StandardCharsets.UTF_8));
        break;
    }
  }

  // this is how TLABProcessor converts event to stack trace
  private String buildBody(RecordedEvent event, RecordedStackTrace stackTrace) {
    String stack = stackSerializer.serialize(stackTrace);
    RecordedThread thread = event.getThread();
    String name = thread == null ? "unknown" : thread.getJavaName();
    long id = thread == null ? 0 : thread.getJavaThreadId();
    return "\""
        + name
        + "\""
        + " #"
        + id
        + " prio=0 os_prio=0 cpu=0ms elapsed=0s tid=0 nid=0 unknown"
        + "\n"
        + "   java.lang.Thread.State: UNKNOWN\n"
        + stack;
  }

  @Test
  public void testWallOfStackToPprof() throws Exception {
    ProfilingSession session = new ProfilingSession();

    try (OutputStream textOut =
        new GZIPOutputStream(new FileOutputStream("/tmp/profile-text.gz"))) {
      InputStream in =
          Files.newInputStream(
              Path.of(
                  "/Users/ltulmin/dev/splunk-otel-java/profiler/src/test/resources/thread-dump1.txt"));
      String threadDumpResult = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      handleWallOfStack(session, textOut, threadDumpResult);
    }

    dump(session.profile.build(), "/tmp/prof.gz");
  }

  public static void dump(Profile profile, String path) throws IOException {
    try (GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(path))) {
      profile.writeTo(outputStream);
      outputStream.finish();
    }
  }

  private void handleWallOfStack(ProfilingSession session, OutputStream textOut, String wallOfStack)
      throws Exception {
    StringTable stringTable = session.stringTable;
    LocationTable locationTable = session.locationTable;

    ThreadDumpToStacks threadDumpToStacks = new ThreadDumpToStacks(new StackTraceFilter(true));
    Stream<String> resultStream = threadDumpToStacks.toStream(wallOfStack);
    List<String> result = resultStream.collect(Collectors.toList());
    for (String s : result) {
      // System.err.println("----");
      StackTrace st = handleStackTrace(s);
      if (!st.stackTraceLines.isEmpty()) {
        textOut.write((s + "\n").getBytes(StandardCharsets.UTF_8));

        Sample.Builder sample = Sample.newBuilder();
        {
          Label.Builder label = Label.newBuilder();
          label.setKey(stringTable.get("header"));
          label.setStr(stringTable.get(st.header));
        }

        {
          Label.Builder label = Label.newBuilder();
          label.setKey(stringTable.get("status"));
          label.setStr(stringTable.get(st.status));
        }

        for (StackTraceLine stl : st.stackTraceLines) {
          sample.addLocationId(locationTable.get(stl.location, stl.classAndMethod, stl.lineNumber));
        }
        session.profile.addSample(sample);
      }
    }
  }

  private static StackTrace handleStackTrace(String stackTrace) {
    String[] lines = stackTrace.split("\\R");
    String header = lines[0];
    String status = lines[1];
    List<StackTraceLine> stackTraceLines = new ArrayList<>();
    for (int i = 2; i < lines.length; i++) {
      StackTraceLine stl = handleStackTraceLine(lines[i]);
      if (stl != null) {
        // System.err.println(stl.classAndMethod + " " + stl.location + " " + stl.lineNumber);
        stackTraceLines.add(stl);
      }
    }

    return new StackTrace(header, status, stackTraceLines);
  }

  private static StackTraceLine handleStackTraceLine(String line) {
    // we expect the stack trace line to look like
    // at java.lang.Thread.run(java.base@11.0.9.1/Thread.java:834)
    if (!line.startsWith("\tat ")) {
      return null;
    }
    if (!line.endsWith(")")) {
      return null;
    }
    // remove "\tat " and trailing ")"
    line = line.substring(4, line.length() - 1);
    int i = line.lastIndexOf('(');
    if (i == -1) {
      return null;
    }
    String classAndMethod = line.substring(0, i);
    String location = line.substring(i + 1);

    i = location.indexOf('/');
    if (i != -1) {
      location = location.substring(i + 1);
    }

    int lineNumber = -1;
    i = location.indexOf(':');
    if (i != -1) {
      try {
        lineNumber = Integer.parseInt(location.substring(i + 1));
      } catch (NumberFormatException ignored) {
      }
      location = location.substring(0, i);
    }

    return new StackTraceLine(classAndMethod, location, lineNumber);
  }

  static class StackTrace {
    final String header;
    final String status;
    final List<StackTraceLine> stackTraceLines;

    StackTrace(String header, String status, List<StackTraceLine> stackTraceLines) {
      this.header = header;
      this.status = status;
      this.stackTraceLines = stackTraceLines;
    }
  }

  static class StackTraceLine {
    final String classAndMethod;
    final String location;
    final int lineNumber;

    StackTraceLine(String classAndMethod, String location, int lineNumber) {
      this.classAndMethod = classAndMethod;
      this.location = location;
      this.lineNumber = lineNumber;
    }
  }

  private static class ProfilingSession {
    Profile.Builder profile = Profile.newBuilder();
    StringTable stringTable = new StringTable(profile);
    FunctionTable functionTable = new FunctionTable(profile, stringTable);
    LocationTable locationTable = new LocationTable(profile, functionTable);
  }

  // copied from
  // https://github.com/bazelbuild/bazel/blob/master/src/main/java/com/google/devtools/build/lib/profiler/memory/AllocationTracker.java
  private static class StringTable {
    final Profile.Builder profile;
    final Map<String, Long> table = new HashMap<>();
    long index = 0;

    StringTable(Profile.Builder profile) {
      this.profile = profile;
      get(""); // 0 is reserved for the empty string
    }

    long get(String str) {
      return table.computeIfAbsent(
          str,
          key -> {
            profile.addStringTable(key);
            return index++;
          });
    }
  }

  private static class FunctionTable {
    final Profile.Builder profile;
    final StringTable stringTable;
    final Map<String, Long> table = new HashMap<>();
    long index = 1; // 0 is reserved

    FunctionTable(Profile.Builder profile, StringTable stringTable) {
      this.profile = profile;
      this.stringTable = stringTable;
    }

    long get(String file, String function) {
      return table.computeIfAbsent(
          file + "#" + function,
          key -> {
            ProfileProto.Function fn =
                ProfileProto.Function.newBuilder()
                    .setId(index)
                    .setFilename(stringTable.get(file))
                    .setName(stringTable.get(function))
                    .build();
            profile.addFunction(fn);
            return index++;
          });
    }
  }

  private static class LocationTable {
    final Profile.Builder profile;
    final FunctionTable functionTable;
    final Map<String, Long> table = new HashMap<>();
    long index = 1; // 0 is reserved

    LocationTable(Profile.Builder profile, FunctionTable functionTable) {
      this.profile = profile;
      this.functionTable = functionTable;
    }

    long get(String file, String function, long line) {
      return table.computeIfAbsent(
          file + "#" + function + "#" + line,
          key -> {
            com.google.perftools.profiles.ProfileProto.Location location =
                com.google.perftools.profiles.ProfileProto.Location.newBuilder()
                    .setId(index)
                    .addLine(
                        ProfileProto.Line.newBuilder()
                            .setFunctionId(functionTable.get(file, function))
                            .setLine(line)
                            .build())
                    .build();
            profile.addLocation(location);
            return index++;
          });
    }
  }
}
