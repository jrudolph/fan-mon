package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }

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

  val ptr = StackValue.get(classOf[CLongPointer])
  ptr.write(0)
  val res = svm.Ioctl.ioctl(fdInt, 0x8008ec10L, ptr)
  println(f"Result: $res ptr: ${ptr.read()}%016x")
}
