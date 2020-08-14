package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }

object IoctlJnaMain extends App {
  val f = new RandomAccessFile("/dev/tuxedo_cc_wmi", "r")

  val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
  fdField.setAccessible(true)
  val fd = fdField.get(f)
  val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
  fdFdField.setAccessible(true)
  val fdInt = fdFdField.get(fd).asInstanceOf[Int]

  println(s"Hello World $fd $fdInt")

  val ptrJna = new Array[Byte](8)
  val res = jna.Ioctl.ioctl(fdInt, 0x8008ec10L, ptrJna)
  println(f"Result: $res ptr: ${ptrJna.map(_ formatted "%02x").mkString}%s")

}
