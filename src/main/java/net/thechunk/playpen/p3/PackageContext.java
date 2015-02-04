package net.thechunk.playpen.p3;

import lombok.Data;

import java.io.File;

@Data
public class PackageContext {
    private PackageManager packageManager;

    private P3Package p3;

    private File destination;
}
