package net.thechunk.playpen.p3;

import java.util.Collection;

public interface IPackageResolver {
    P3Package resolvePackage(PackageManager pm, String id, String version);
    Collection<P3Package.P3PackageInfo> getPackageList(PackageManager pm);
}
