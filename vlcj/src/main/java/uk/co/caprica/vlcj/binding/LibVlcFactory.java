/*
 * This file is part of VLCJ.
 *
 * VLCJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VLCJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VLCJ.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright 2009, 2010, 2011 Caprica Software Limited.
 */

package uk.co.caprica.vlcj.binding;

import java.lang.reflect.Proxy;
import java.text.MessageFormat;

import uk.co.caprica.vlcj.logger.Logger;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;
import uk.co.caprica.vlcj.version.Version;

/**
 * A factory that creates interfaces to the libvlc native library.
 */
public class LibVlcFactory {

  /**
   * Help text if the native library failed to load.
   */
  private static final String NATIVE_LIBRARY_HELP = 
    "Failed to load the native library.\n\n" +
    "The error was \"{0}\".\n\n" +
  	"The required native libraries are named \"{1}\" and \"{2}\".\n\n" +
  	"In the text below <libvlc-path> represents the name of the directory containing \"{1}\" and \"{2}\".\n\n" +
  	"There are a number of different ways to specify where to find the native libraries:\n" +
  	" 1. Include NativeLibrary.addSearchPath(\"{3}\", \"<libvlc-path>\"); at the start of your application code.\n" +
    " 2. Include System.setProperty(\"jna.library.path\", \"<libvlc-path>\"); at the start of your application code.\n" +
  	" 3. Specify -Djna.library.path=<libvlc-path> on the command-line when starting your application.\n" +
  	" 4. Add <libvlc-path> to the system search path (and reboot).\n\n" +
  	"More information may be available in the log, specify -Dvlcj.log=DEBUG on the command-line when starting your application.\n";
  
  /**
   * True if the access to the native library should be synchronised.
   */
  private boolean synchronise;

  /**
   * True if the access to the native library should be logged.
   */
  private boolean log;
  
  /**
   * At least this native library version is required.
   */
  private Version requiredVersion;
  
  /**
   * Private constructor prevents direct instantiation by others.
   */
  private LibVlcFactory() {
  }

  /**
   * Create a new factory instance
   * 
   * @return factory
   */
  public static LibVlcFactory factory() {
    return new LibVlcFactory();
  }
  
  /**
   * Request that the libvlc native library instance be synchronised.
   * 
   * @return factory
   */
  public LibVlcFactory synchronise() {
    this.synchronise = true;
    return this;
  }
  
  /**
   * Request that the libvlc native library instance be logged.
   * 
   * @return factory
   */
  public LibVlcFactory log() {
    this.log = true;
    return this;
  }
  
  /**
   * Request that the libvlc native library be of at least a particular version.
   * <p>
   * The version check can be disabled by specifying the following system 
   * property:
   * <pre>
   *   -Dvlcj.check=no
   * </pre>
   * 
   * @param version required version
   * @return factory
   */
  public LibVlcFactory atLeast(String version) {
    this.requiredVersion = new Version(version);
    return this;
  }
  
  /**
   * Create a new libvlc native library instance.
   * 
   * @return native library instance
   * @throws RuntimeException if a minimum version check was specified and failed 
   */
  public LibVlc create() {
    // Synchronised or not...
    try {
      LibVlc instance = synchronise ? LibVlc.SYNC_INSTANCE : LibVlc.INSTANCE;
      // Logged...
      if(log) {
        instance = (LibVlc)Proxy.newProxyInstance(LibVlc.class.getClassLoader(), new Class<?>[] {LibVlc.class}, new LoggingProxy(instance));
      }
      String nativeVersion = instance.libvlc_get_version();
      Logger.info("vlc: {}, changeset {}", nativeVersion, LibVlc.INSTANCE.libvlc_get_changeset());
      Logger.info("libvlc: {}", getNativeLibraryPath(instance));
      if(requiredVersion != null) {
        try {
          Version actualVersion = new Version(nativeVersion);
          if(!actualVersion.atLeast(requiredVersion)) {
            if(!"no".equalsIgnoreCase(System.getProperty("vlcj.check"))) {
              Logger.fatal("This version of vlcj requires version {} or later of libvlc, found too old version {}", requiredVersion, actualVersion);
              Logger.fatal("You can suppress this check by specifying -Dvlcj.check=no but some functionality will be unavailable and cause failures.");
              throw new RuntimeException("This version of vlcj requires version " + requiredVersion + " or later of libvlc, found too old version " + actualVersion);
            }
            else {
              Logger.warn("This version of vlcj requires version {} or later of libvlc, found too old version {}. Fatal run-time failures may occur.", requiredVersion, actualVersion);
            }
          }
        }
        catch(Throwable t) {
          if(!"no".equalsIgnoreCase(System.getProperty("vlcj.check"))) {
            Logger.fatal("Unable to check the native library version '{}' because of {}", nativeVersion, t);
            Logger.fatal("You can suppress this check by specifying -Dvlcj.check=no but some functionality will be unavailable and cause failures.");
            throw new RuntimeException("Unable to check the native library version " + nativeVersion, t);
          }
          else {
            Logger.warn("Unable to check the native library version '{}'. Fatal run-time failures may occur.", nativeVersion);
          }
        }
      }
      return instance;
    }
    catch(UnsatisfiedLinkError e) {
      Logger.error("Failed to load native library");
      String msg = MessageFormat.format(NATIVE_LIBRARY_HELP, new Object[] {e.getMessage(), RuntimeUtil.getLibVlcName(), RuntimeUtil.getLibVlcCoreName(), RuntimeUtil.getLibVlcLibraryName()});
      throw new RuntimeException(msg);
    }
  }

  /**
   * Parse out the complete file path of the native library.
   * <p>
   * This depends on the format of the toString() of the JNA implementation
   * class.
   * 
   * @param library native library instance
   * @return native library path, or simply the toString of the instance if the path could not be parsed out
   */
  private static String getNativeLibraryPath(Object library) {
    String s = library.toString();
    int start = s.indexOf('<');
    if(start != -1) {
      start++;
      int end = s.indexOf('@', start);
      if(end != -1) {
        s = s.substring(start, end);
        return s;
      }
    }
    return s;
  }
}
