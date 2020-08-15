package net.virtualvoid.sensors

object FanMonitorJna extends App {
  new FanMonitor(jna.Ioctl.ioctl)
}

