package net.virtualvoid.sensors.svm;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;

@CContext(IoctlDirectives.class)
public class Ioctl {
    @CFunction
    public static native int ioctl(int fd, long request, PointerBase data);
}