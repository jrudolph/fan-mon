package net.virtualvoid.sensors.jna;

import com.sun.jna.Native;
import com.sun.jna.Platform;

public class Ioctl {
    static {
        Native.register(Platform.C_LIBRARY_NAME);
    }

    public static native int ioctl(int fd, long request, byte[] data);
}
