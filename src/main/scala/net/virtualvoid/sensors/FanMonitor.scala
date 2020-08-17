package net.virtualvoid.sensors

import java.io.{ FileDescriptor, RandomAccessFile }

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

case class FanInfo(
    speed:              Double,
    temperatureCelsius: Byte
)
trait ClevoFan {
  def fanInfo(): FanInfo
}
object ClevoFan {
  def apply(ioctl: (Int, Long, Array[Byte]) => Int): ClevoFan = new ClevoFan {
    val (f, tuxedoWmiFd: Int) = {
      val f = new RandomAccessFile("/dev/tuxedo_cc_wmi", "r")
      val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
      fdField.setAccessible(true)
      val fd = fdField.get(f)
      val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
      fdFdField.setAccessible(true)
      (f, fdFdField.get(fd).asInstanceOf[Int])
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
    def toBar(value: Double): Char = Ansi.bars(((value - minValue) * 6 / span).toInt.max(0).min(5))
    val latest = values.last
    val bars = values.map(toBar).mkString
    f"${sensor.label.take(12)}%-12s ${bars formatted "%-" + barWidth + "s"}%s $latest%7.1f ${sensor.unit}%s\n"
  }

  def historyLineDouble(history: mutable.Seq[Double], sensor: Sensor): String = {
    def color(level: Int): String = {
      if (level < 4) Console.GREEN
      else if (level < 9) Console.YELLOW
      else Console.RED
    }

    val values =
      if (sensor.isCumulative)
        history.takeRight(barWidth + 1).sliding(2).map {
          case ArrayBuffer(i0: Double, i1: Double) if !i0.isNaN && !i1.isNaN => i1 - i0
          case _ => Double.NaN // FIXME
        }.toVector
      else history.takeRight(barWidth)
    val minValue = sensor.minValueOption.getOrElse(values.min)
    val maxValue = sensor.maxValueOption.getOrElse(values.max)
    val span = maxValue - minValue
    def toBar0(value: Double): String =
      if (value.isNaN) " "
      else {
        val level = ((value - minValue) * 12 / span).toInt
        s"${color(level)}${Ansi.bars((level - 6).max(0).min(5))}"
      }
    def toBar1(value: Double): String =
      if (value.isNaN) s"${Console.RED}x"
      else {
        val level = ((value - minValue) * 12 / span).toInt
        s"${color(level)}${Ansi.bars(level.max(0).min(5))}"
      }
    val latest = values.last
    val bars0 = values.map(toBar0).mkString
    val bars1 = values.map(toBar1).mkString
    val error = if (latest.isNaN) Try(sensor.read()).fold[String](_.getMessage, _ => "") else ""

    f"${""}%-12s $bars0%s${Console.RESET}$error\n" +
      f"${sensor.label.take(12)}%-12s $bars1%s${Console.RESET} $latest%7.1f ${sensor.unit}%s\n"
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
  val fanTemp = Sensor("Fan temp", () => fan.fanInfo().temperatureCelsius, Some(55), Some(105), "°C")
  val packageEnergy = Sensor("Pkg energy", () => readEnergyCumulativeMicroJoule() / 1000, Some(0), Some(40000), "mW", isCumulative = true)
  val coreEnergy = Sensor("Core energy", () => readCoreEnergyCumulativeMicroJoule() / 1000, Some(0), Some(40000), "mW", isCumulative = true)
  val uncoreEnergy = Sensor("Uncore energy", () => readUncoreEnergyCumulativeMicroJoule() / 1000, Some(0), Some(40000), "mW", isCumulative = true)
  def coreThermalThrottlePackageCountSensor(core: Int): Sensor =
    Sensor(s"Core $core Throttle", () => thermalThrottlePackageCount(core)() / 1000, None, None, "", isCumulative = true)
  def coreFreqSensor(core: Int): Sensor =
    Sensor(s"Core $core freq", () => coreFreq(core)() / 1000, Some(1200), Some(4000), "MHz")

  val gpuFreq = Sensor("GPU freq", () => igpuFreq().toDouble, Some(350), Some(1300), "MHz")

  val sensors = Seq(
    fanSpeed, fanTemp, packageEnergy, coreEnergy, uncoreEnergy, coreThermalThrottlePackageCountSensor(0), gpuFreq
  ) ++ (0 to 3).map(coreFreqSensor)

  val data: Seq[(Sensor, ArrayBuffer[Double])] = sensors.map(_ -> new ArrayBuffer[Double]())

  println()
  print(Ansi.SaveCursor)

  data.foreach {
    case (s, b) if s.isCumulative => b.addOne(Try(s.read()).getOrElse(Double.NaN))
    case _                        =>
  }

  while (true) {
    data.foreach {
      case (s, b) => b.addOne(Try(s.read()).getOrElse(Double.NaN))
    }

    print(Ansi.EraseDisplayBelow)

    data.foreach {
      case (s, b) => print(historyLineDouble(b, s))
    }

    print(s"\u001b[${sensors.size * 2}A")

    Thread.sleep(1000)
  }

  def coreFreq(core: Int): () => Long =
    readLongFromFile(s"/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")

  def readEnergyCumulativeMicroJoule: () => Long =
    readLongFromFile("/sys/devices/virtual/powercap/intel-rapl/intel-rapl:0/energy_uj")

  def readCoreEnergyCumulativeMicroJoule: () => Long =
    readLongFromFile("/sys/devices/virtual/powercap/intel-rapl/intel-rapl:0/intel-rapl:0:0/energy_uj")

  def readUncoreEnergyCumulativeMicroJoule: () => Long =
    readLongFromFile("/sys/devices/virtual/powercap/intel-rapl/intel-rapl:0/intel-rapl:0:1/energy_uj")

  def thermalThrottlePackageCount(core: Int): () => Long =
    readLongFromFile(s"/sys/devices/system/cpu/cpu$core/thermal_throttle/package_throttle_count")

  def igpuFreq: () => Long =
    readLongFromFile("/sys/class/drm/card0/gt_cur_freq_mhz")

  def readLongFromFile(fileName: String): () => Long =
    () => Source.fromFile(fileName).getLines().next().toLong // FIXME: crappy implementation
}