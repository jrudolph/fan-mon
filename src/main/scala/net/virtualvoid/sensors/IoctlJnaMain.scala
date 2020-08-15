package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }

case class FanInfo(
    speed:              Double,
    temperatureCelsius: Byte
)
trait ClevoFan {
  def fanInfo(): FanInfo
}
object ClevoFan {
  def apply(): ClevoFan = new ClevoFan {
    val tuxedoWmiFd: Int = {
      val f = new RandomAccessFile("/dev/tuxedo_cc_wmi", "r")
      val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
      fdField.setAccessible(true)
      val fd = fdField.get(f)
      val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
      fdFdField.setAccessible(true)
      fdFdField.get(fd).asInstanceOf[Int]
    }
    override def fanInfo(): FanInfo = {
      val bytes = new Array[Byte](8)
      val res = jna.Ioctl.ioctl(tuxedoWmiFd, 0x8008ec10L, bytes)
      require(res == 0, s"Ioctl result was != 0: $res")
      FanInfo(
        (bytes(0) & 0xff).toDouble / 256,
        bytes(1)
      )
    }
  }
}

object FanMonitor extends App {
  val fan = ClevoFan()
  Iterator.continually {
    Thread.sleep(500)
    fan.fanInfo()
  }.take(10).foreach(println)
}