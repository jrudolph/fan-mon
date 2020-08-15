package net.virtualvoid.sensors

import java.nio.{ ByteBuffer, ByteOrder }

import org.graalvm.nativeimage.StackValue
import org.graalvm.nativeimage.c.`type`.CLongPointer

object FanMonitorSvm extends App {
  new FanMonitor({ (fd, request, data) =>
    val ptr = StackValue.get(classOf[CLongPointer])
    val buffer = ByteBuffer.wrap(data)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val l = buffer.getLong()
    ptr.write(l)
    val res = svm.Ioctl.ioctl(fd, request, ptr)
    buffer.flip()
    buffer.putLong(ptr.read())
    res
  })
}