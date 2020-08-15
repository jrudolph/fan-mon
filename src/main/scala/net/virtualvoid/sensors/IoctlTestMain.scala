package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }
import java.nio.{ ByteBuffer, ByteOrder }

import org.graalvm.nativeimage.StackValue
import org.graalvm.nativeimage.c.`type`.CLongPointer

object IoctlTestMain extends App {
  val f = new RandomAccessFile("/dev/tuxedo_cc_wmi", "r")

  val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
  fdField.setAccessible(true)
  val fd = fdField.get(f)
  val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
  fdFdField.setAccessible(true)
  val fdInt = fdFdField.get(fd).asInstanceOf[Int]

  println(s"Hello World $fd $fdInt")

  val ioctl = { (fd: Int, request: Long, data: Array[Byte]) =>
    val ptr = StackValue.get(classOf[CLongPointer])
    val buffer = ByteBuffer.wrap(data)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val l = buffer.getLong()
    ptr.write(l)
    val res = svm.Ioctl.ioctl(fd, request, ptr)
    buffer.flip()
    buffer.putLong(ptr.read())
    res
  }

  /*val ptr = StackValue.get(classOf[CLongPointer])
  ptr.write(0)
  val res = svm.Ioctl.ioctl(fdInt, 0x8008ec10L, ptr)
  println(f"Result: $res ptr: ${ptr.read()}%016x")*/
  val fan = ClevoFan(ioctl)
  println(fan.fanInfo())
  println(fan.fanInfo())
  println(fan.fanInfo())
}
