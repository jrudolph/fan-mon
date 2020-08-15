#/bin/sh

set -ex

native-image \
  --no-fallback -H:+ReportExceptionStackTraces \
  -cp target/scala-2.13/graal-ioctl-test-assembly-0.1.0-SNAPSHOT.jar \
  net.virtualvoid.sensors.FanMonitorSvm \
  fan-mon

#  -H:+AllowVMInspection \
#  -H:+PrintHeapHistogram \
# -cp tmptest \
#  -H:+LogVerbose \
#  -H:+DashboardAll \
#  -H:+PrintUniverse \
#  -H:+RemoveUnusedSymbols \
#  -H:GenerateDebugInfo=0 \

sudo ./fan-mon