/** This exception may be thrown by parsers. */

package com.cloudwalk.framework3d;

import java.io.*;

public class FileFormatException extends IOException {
    public FileFormatException(String s) { super(s); }
}
