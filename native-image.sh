#/bin/sh

set -ex

native-image --no-fallback -H:+ReportExceptionStackTraces \
  -cp target/scala-2.13/graal-ioctl-test-assembly-0.1.0-SNAPSHOT.jar \
  net.virtualvoid.sensors.IoctlTestMain \
  ioctl-test

sudo ./ioctl-test