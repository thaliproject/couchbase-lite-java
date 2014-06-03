package com.couchbase.test.lite;

// https://github.com/couchbase/couchbase-lite-android/issues/285

import java.io.*;
import java.nio.file.*;

/**
 * Provides a platform specific way to create a safe temporary directory location since this is different in Java
 * and Android
 */
public class LiteTestContextBase {
    // There is an expectation in the tests that the root directory is the same for all contexts, so we simulate
    // that by fixing the rootDirectory for the lifetime of the tests.
    private static File rootDirectory = null;

    public LiteTestContextBase() {
        if (rootDirectory == null) {
            try {
                rootDirectory = Files.createTempDirectory("couchbaselitetest").toFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create temp directory!", e);
            }
        }
    }

    public File getRootDirectory() {
        return rootDirectory;
    }
}
