package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class FanInfo(
    speed:              Double,
    temperatureCelsius: Byte
)
trait ClevoFan {
  def fanInfo(): FanInfo
}
object ClevoFan {
  def apply(ioctl: (Int, Long, Array[Byte]) => Int): ClevoFan = new ClevoFan {
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
      val res = ioctl(tuxedoWmiFd, 0x8008ec10L, bytes)
      require(res == 0, s"Ioctl result was != 0: $res")
      FanInfo(
        (bytes(0) & 0xff).toDouble / 255,
        bytes(1)
      )
    }
  }
}

object Ansi {
  private val graphDots =
    """⠀⢀⢠⢰⢸
      |⡀⣀⣠⣰⣸
      |⡄⣄⣤⣴⣼
      |⡆⣆⣦⣶⣾
      |⡇⣇⣧⣷⣿""".stripMargin.filterNot(_ == '\n')

  def dots(i0: Int, i1: Int): Char = graphDots(i0 + 5 * i1)

  val bars = " ▁▂▃▅▇"

  val SaveCursor = "\u001b7"
  val RestoreCursor = "\u001b8"
  val EraseDisplayBelow = "\u001b\u005b0J"
}

class FanMonitor(ioctl: (Int, Long, Array[Byte]) => Int) {
  val fan = ClevoFan(ioctl)

  val barWidth = 80

  def historyLine(history: mutable.Seq[Double], sensor: Sensor): String = {
    val values =
      if (sensor.isCumulative)
        history.takeRight(barWidth + 1).sliding(2).map {
          case ArrayBuffer(i0: Double, i1: Double) => i1 - i0
          case ArrayBuffer(i0: Double)             => 0 // FIXME
        }.toVector
      else history.takeRight(barWidth)
    val minValue = sensor.minValueOption.getOrElse(values.min)
    val maxValue = sensor.maxValueOption.getOrElse(values.max)
    val span = maxValue - minValue
    def toBar(value: Double): Char = Ansi.bars(((value - minValue) * 5 / span).toInt.max(0).min(4))
    val latest = values.last
    val bars = values.map(toBar).mkString
    f"${sensor.label.take(12)}%-12s ${bars formatted "%-" + barWidth + "s"}%s $latest%7.1f ${sensor.unit}%s\n"
  }

  case class Sensor(
      label:          String,
      read:           () => Double,
      minValueOption: Option[Double],
      maxValueOption: Option[Double],
      unit:           String,
      isCumulative:   Boolean        = false
  )

  val fanSpeed = Sensor("Fan speed", () => fan.fanInfo().speed * 100, Some(0), Some(100d), "%")
  val fanTemp = Sensor("Fan temp", () => fan.fanInfo().temperatureCelsius, Some(30), Some(105), "°C")
  val packageEnergy = Sensor("CPU energy", () => readEnergyCumulativeMicroJoule() / 1000, Some(0), Some(40000), "mW", isCumulative = true)
  def coreFreqSensor(core: Int): Sensor =
    Sensor(s"Core $core freq", () => coreFreq(core)() / 1000, Some(1200), Some(4000), "MHz")

  val sensors = Seq(
    fanSpeed, fanTemp, packageEnergy
  ) ++ (0 to 3).map(coreFreqSensor)

  val data: Seq[(Sensor, ArrayBuffer[Double])] = sensors.map(_ -> new ArrayBuffer[Double]())

  println()
  print(Ansi.SaveCursor)

  data.foreach {
    case (s, b) if s.isCumulative => b.addOne(s.read())
    case _                        =>
  }

  while (true) {
    data.foreach {
      case (s, b) => b.addOne(s.read())
    }

    print(Ansi.EraseDisplayBelow)

    data.foreach {
      case (s, b) => print(historyLine(b, s))
    }

    print(s"\u001b[${sensors.size}A")

    Thread.sleep(1000)
  }

  def coreFreq(core: Int): () => Long =
    readLongFromFile(s"/sys/devices/system/cpu/cpu${core}/cpufreq/scaling_cur_freq")

  def readEnergyCumulativeMicroJoule: () => Long =
    readLongFromFile("/sys/devices/virtual/powercap/intel-rapl/intel-rapl:0/energy_uj")

  def readLongFromFile(fileName: String): () => Long =
    () => Source.fromFile(fileName).getLines().next().toLong // FIXME: crappy implementation
}