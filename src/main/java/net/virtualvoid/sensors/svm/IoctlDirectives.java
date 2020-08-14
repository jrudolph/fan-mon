package net.virtualvoid.sensors.svm;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IoctlDirectives implements CContext.Directives {
    private static final String[] libs = new String[]{
        "<sys/ioctl.h>",
    };

    @Override
    public boolean isInConfiguration() {
        return Platform.includedIn(Platform.LINUX.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        return new ArrayList<>(Arrays.asList(libs));
    }
}