/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dalvik.system;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Base class for common functionality between various dex-based
 * {@link ClassLoader} implementations.
 */
public class BaseDexClassLoader extends ClassLoader {
    //其继承 ClassLoader 实现的 findClass() 、findResource() 均是基于 pathList 来实现的
    private final DexPathList pathList;

    /**
     * Constructs an instance.
     *
     * @param dexPath            the list of jar/apk files containing classes and
     *                           resources, delimited by {@code File.pathSeparator}, which
     *                           defaults to {@code ":"} on Android
     *                           指目标类所在的APK或jar文件路径，类加载器将从该路径中寻找指定的目标类，该类必须是APK或者jar的全路径如果要包括多个路径，路径之间必须使用指定的分隔符，可以使用System.getProperty("path
     *                           .separtor"),DexClassLoader 中所谓的支持加载APK，jar，dex,也可以从SD卡中加载，指的就是这个路径，最终是将dexPath路径上的文件ODEX优化到optimizedDirectoyr,然后进行加载
     * @param optimizedDirectory directory where optimized dex files   优化目录
     *                           should be written; may be {@code null}
     *                           由于dex文件被包含在APK或者jar中，因此在装载目标类之前需要从APK或者JAR文件中解压dex文件，该参数就是定制解压出来的dex文件存放的路径的，这也是对apk的dex根据平台进行ODE优化的过程，其实APK
     *                           是一个压缩包，里面包括dex文件，ODEX文件的优化就是把包里面的执行程序提取出来，就变成ODEX文件，因为你提取出来了，系统第一次启动的时候就不用去解压程序压缩包里面的程序，少了一个解压过程，这样系统启动就加快，
     *                           为什么说是第一次呢？因为DEX版本只会有在第一次会解压执行程序到/data/dalvik-cache(针对PathClassLoder)或者optimizedDirectory(针对DexClassLoader)
     *                           目录，之后也就直接读取dex文件，所以第二次启动就和正常启动差不多了，当然这只是简单理解，实际生成ODEX还有一定的优化作用，ClassLoader只能加载内部存储路径中的dex文件，所以这个路径必须是内部路径
     * @param libraryPath        the list of directories containing native
     *                           libraries, delimited by {@code File.pathSeparator}; may be
     *                           {@code null}
     *                           指的是目标所使用的c/c++库存放的路径
     * @param parent             the parent class loader  指该加载器的父加载器，一般为当前执行类的加载器，例如Android中以context.getClassLoader()作为父加载器
     */
    public BaseDexClassLoader(String dexPath, File optimizedDirectory, String libraryPath, ClassLoader parent) {
        super(parent);
        this.pathList = new DexPathList(this, dexPath, libraryPath, optimizedDirectory);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Throwable> suppressedExceptions = new ArrayList<Throwable>();
        Class c = pathList.findClass(name, suppressedExceptions);
        if (c == null) {
            ClassNotFoundException cnfe = new ClassNotFoundException("Didn't find class \"" + name + "\" on path: " + pathList);
            for (Throwable t : suppressedExceptions) {
                cnfe.addSuppressed(t);
            }
            throw cnfe;
        }
        return c;
    }

    @Override
    protected URL findResource(String name) {
        return pathList.findResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        return pathList.findResources(name);
    }

    @Override
    public String findLibrary(String name) {
        return pathList.findLibrary(name);
    }

    /**
     * Returns package information for the given package.
     * Unfortunately, instances of this class don't really have this
     * information, and as a non-secure {@code ClassLoader}, it isn't
     * even required to, according to the spec. Yet, we want to
     * provide it, in order to make all those hopeful callers of
     * {@code myClass.getPackage().getName()} happy. Thus we construct
     * a {@code Package} object the first time it is being requested
     * and fill most of the fields with dummy values. The {@code
     * Package} object is then put into the {@code ClassLoader}'s
     * package cache, so we see the same one next time. We don't
     * create {@code Package} objects for {@code null} arguments or
     * for the default package.
     * <p>
     * <p>There is a limited chance that we end up with multiple
     * {@code Package} objects representing the same package: It can
     * happen when when a package is scattered across different JAR
     * files which were loaded by different {@code ClassLoader}
     * instances. This is rather unlikely, and given that this whole
     * thing is more or less a workaround, probably not worth the
     * effort to address.
     *
     * @param name the name of the class
     * @return the package information for the class, or {@code null}
     * if there is no package information available for it
     */
    @Override
    protected synchronized Package getPackage(String name) {
        if (name != null && !name.isEmpty()) {
            Package pack = super.getPackage(name);

            if (pack == null) {
                pack = definePackage(name, "Unknown", "0.0", "Unknown", "Unknown", "0.0", "Unknown", null);
            }

            return pack;
        }

        return null;
    }

    /**
     * @hide
     */
    public String getLdLibraryPath() {
        StringBuilder result = new StringBuilder();
        for (File directory : pathList.getNativeLibraryDirectories()) {
            if (result.length() > 0) {
                result.append(':');
            }
            result.append(directory);
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + pathList + "]";
    }
}
