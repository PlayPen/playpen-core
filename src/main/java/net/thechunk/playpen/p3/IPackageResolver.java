package net.thechunk.playpen.p3;

public interface IPackageResolver {
    P3Package resolvePackage(PackageManager pm, String id, String version);
}
