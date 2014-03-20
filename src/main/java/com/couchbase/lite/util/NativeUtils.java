package com.couchbase.lite.util;

import com.couchbase.lite.Native;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NativeUtils {
    public static final String TAG = "Native";
    private static final Map<String, Boolean> LOADED_LIBRARIES = new HashMap<String, Boolean>();
    
    public static void main(String[] args) {
        for (int i=0; i<args.length; i++) {
            if ("--libraryPath".equals(args[i]) && args.length > i+1) {
                String libraryName = args[i+1];
                String libraryPath = _getConfiguredLibraryPath(libraryName);
                
                if (libraryPath == null) {
                    libraryPath = _getLibraryResourcePath(libraryName);
                }
            } 
        }
    }

    public static void loadLibrariesFromJar(String rootLibraryName)  {
        if (LOADED_LIBRARIES.containsKey(rootLibraryName)) {
            return;
        }

        String libraryPath = basePathForLibrary();
        // Taken from http://stackoverflow.com/questions/749533/how-to-walk-through-java-class-resources
        URL url = Native.class.getResource("Native.class");
        String scheme = url.getProtocol();
        if ("jar".equals(scheme) == false) {
            throw new RuntimeException("Scheme somehow doesn't equal to jar, how is that possible?");
        }
        try {
            JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
            // Creates a unique temp directory so as to prevent any naming collisions with others using temp
            File targetFolder = Files.createTempDirectory("couchbase-lite-java").toFile();
            // It's polite to clean up after oneself
            targetFolder.deleteOnExit();
            JarFile jarFile = jarURLConnection.getJarFile();
            Enumeration<JarEntry> entryEnumeration = jarFile.entries();
            while(entryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = entryEnumeration.nextElement();
                String fullPath = "/" + jarEntry.getName();
                if (jarEntry.isDirectory() == false && fullPath.startsWith(libraryPath)) {
                    File targetLibrary = extractLibrary(fullPath, targetFolder);
                    // Note: Libraries listed in the JAR MUST be in the right dependency order or you will get
                    // an UnsatisfiedLinkError and yes this is fragile as all heck
                    System.load(targetLibrary.getAbsolutePath());
                }
            }
            LOADED_LIBRARIES.put(rootLibraryName, true);
        } catch (IOException e) {
            throw new RuntimeException("How did we get an IOException?", e);
        }
    }

    public static void loadLibrary(String libraryName) {
        // If the library has already been loaded then no need to reload.
        if (LOADED_LIBRARIES.containsKey(libraryName)) return;
        
        try {
            File libraryFile = null;
            
            String libraryPath = _getConfiguredLibraryPath(libraryName);

            if (libraryPath != null) {
                // If library path is configured then use it.
                libraryFile = new File(libraryPath);
            } else {
                libraryFile = _extractLibrary(libraryName);
            }

            System.load("C:\\Users\\yarong\\AppData\\Local\\Temp\\libgcc_s_dw2-1.dll");
            System.load(libraryFile.getAbsolutePath());

            LOADED_LIBRARIES.put(libraryName, true);
        } catch (Exception e) {
            Log.e(TAG, "Error loading library: " + libraryName, e);
        }
    }
    
    private static String _getConfiguredLibraryPath(String libraryName) {
        String key = String.format("com.couchbase.lite.lib.%s.path", libraryName);
        
        return System.getProperty(key);
    }
    
    private static String _getLibraryFullName(String libraryName) {
        String name = System.mapLibraryName(libraryName);

        return name;
    }

    private static File extractLibrary(String libraryPath, File targetFolder) throws IOException {
        String libraryName = new File(libraryPath).getName();

        File targetFile = new File(targetFolder, libraryName);

        // If the target already exists, and it's unchanged, then use it, otherwise delete it and
        // it will be replaced.
        if (targetFile.exists()) {
            // Remove old native library file.
            if (!targetFile.delete()) {
                // If we can't remove the old library file then log a warning and try to use it.
                Log.w(TAG, "Failed to delete existing library file: " + targetFile.getAbsolutePath());
                return targetFile;
            }
        }

        // Extract the library to the target directory.
        InputStream libraryReader = NativeUtils.class.getResourceAsStream(libraryPath);
        if (libraryReader == null) {
            Log.e(TAG, "Library not found: " + libraryPath);
            return null;
        }

        FileOutputStream libraryWriter = new FileOutputStream(targetFile);
        try {
            byte[] buffer = new byte[1024];
            int bytesRead = 0;

            while ((bytesRead = libraryReader.read(buffer)) != -1) {
                libraryWriter.write(buffer, 0, bytesRead);
            }
        } finally {
            libraryWriter.close();
            libraryReader.close();
        }

        // On non-windows systems set up permissions for the extracted native library.
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            try {
                Runtime.getRuntime().exec(new String[] {"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
            } catch (Throwable e) {
                Log.w(TAG, "Error executing 'chmod 755' on extracted native library", e);
            }
        }

        return targetFile;
    }

    private static File _extractLibrary(String libraryName) throws IOException {
        String libraryResourcePath = _getLibraryResourcePath(libraryName);
        String targetFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();

        File targetFile = new File(targetFolder, _getLibraryFullName(libraryName));

        // If the target already exists, and it's unchanged, then use it, otherwise delete it and
        // it will be replaced.
        if (targetFile.exists()) {
            // Remove old native library file.
            if (!targetFile.delete()) {
                // If we can't remove the old library file then log a warning and try to use it.
                Log.w(TAG, "Failed to delete existing library file: " + targetFile.getAbsolutePath());
                return targetFile;
            }
        }

        // Extract the library to the target directory.
        InputStream libraryReader = NativeUtils.class.getResourceAsStream(libraryResourcePath);
        if (libraryReader == null) {
            Log.e(TAG, "Library not found: " + libraryResourcePath);
            return null;
        }

        FileOutputStream libraryWriter = new FileOutputStream(targetFile);
        try {
            byte[] buffer = new byte[1024];
            int bytesRead = 0;

            while ((bytesRead = libraryReader.read(buffer)) != -1) {
                libraryWriter.write(buffer, 0, bytesRead);
            }
        } finally {
            libraryWriter.close();
            libraryReader.close();
        }

        // On non-windows systems set up permissions for the extracted native library.
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            try {
                Runtime.getRuntime().exec(new String[] {"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
            } catch (Throwable e) {
                Log.w(TAG, "Error executing 'chmod 755' on extracted native library", e);
            }
        }

        return targetFile;
    }
    
//    private static File _extractLibrary(String libraryName) throws IOException {
//        String libraryResourcePath = _getLibraryResourcePath(libraryName);
//        String targetFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
//        File targetFile = new File(targetFolder, _getLibraryFullName(libraryName));
//        
//        // If the target already exists, and it's unchanged, then use it, otherwise delete it and
//        // it will be replaced.
//        if (targetFile.exists()) {
//            // Compare MD5's for existing and current libraries (i.e. has it changed).
//            String hash1 = _hash(NativeUtils.class.getResourceAsStream(libraryResourcePath));
//            String hash2 = _hash(new FileInputStream(targetFile));
//
//            if (hash1 != null && hash1.equals(hash2)) {
//                return targetFile;
//            } else {
//                // Remove old native library file.
//                if (!targetFile.delete()) {
//                    // If we can't remove the old library file then log a warning and try to use it.
//                    Log.w(TAG, "Failed to delete existing library file: " + targetFile.getAbsolutePath());
//                    return targetFile;
//                }
//            }
//        }
//        
//        // Extract the library to the target directory.
//        InputStream libraryReader = NativeUtils.class.getResourceAsStream(libraryResourcePath);
//        if (libraryReader == null) {
//            Log.e(TAG, "Library not found: " + libraryResourcePath);
//            return null;
//        }
//        
//        FileOutputStream libraryWriter = new FileOutputStream(targetFile);
//        try {
//            byte[] buffer = new byte[1024];
//            int bytesRead = 0;
//            
//            while ((bytesRead = libraryReader.read(buffer)) != -1) {
//                libraryWriter.write(buffer, 0, bytesRead);
//            }
//        } finally {
//            libraryWriter.close();
//            libraryReader.close();
//        }
//        
//        // On non-windows systems set up permissions for the extracted native library.
//        if (!System.getProperty("os.name").contains("Windows")) {
//            try {
//                Runtime.getRuntime().exec(new String[] {"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
//            } catch (Throwable e) {
//                Log.w(TAG, "Error executing 'chmod 755' on extracted native library", e);
//            }
//        }
//
//        return targetFile;
//    }

    private static String basePathForLibrary() {
        // Root native folder.
        String path = "/native";

        // OS part of path.
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            path += "/linux";
        } else if (osName.contains("Mac")) {
            path += "/osx";
        } else if (osName.contains("Windows")) {
            path += "/windows";
        } else {
            path += "/" + osName.replaceAll("\\W", "").toLowerCase();
        }

        // Architecture part of path.
        String archName = System.getProperty("os.arch");
        path += "/" + archName.replaceAll("\\W", "");

        return path + "/";
    }

    private static String _getLibraryResourcePath(String libraryName) {
        return basePathForLibrary() + _getLibraryFullName(libraryName);
    }
    
//  private static String _hash(InputStream input) throws IOException {
//      if (input == null) return null;
//      
//      try {
//          MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
//          DigestInputStream digestInputStream = new DigestInputStream(input, digest);
//          
//          while (digestInputStream.read() >= 0) {
//              // Read through the digest input stream which updates the assigned digest.
//          }
//          
//          ByteArrayOutputStream md5 = new ByteArrayOutputStream();
//          md5.write(digest.digest());
//          
//          return md5.toString();
//      } catch (NoSuchAlgorithmException e) {
//          Log.w(SQLiteStorageEngine.TAG, "MD5 algorithm is not found", e);
//          return null;
//      }
//  }
}
