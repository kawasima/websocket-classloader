package net.unit8.wscl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The utility for properties.
 *
 * @author kawasima
 */
public class PropertyUtils {
    private static final Logger logger = LoggerFactory.getLogger(PropertyUtils.class);
    private static final Pattern VAR_PTN = Pattern.compile("\\$\\{([^\\}]+)\\}");

    private static String replace(String value) {
        StringBuffer sb = new StringBuffer(256);
        Matcher m = VAR_PTN.matcher(value);
        while (m.find()) {
            String propValue = System.getProperty(m.group(1));
            if (propValue == null)
                propValue = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(propValue));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Long getLongSystemProperty(String name, long defaultValue) {
        String longStr = System.getProperty(name);
        try {
            if (longStr != null)
                return Long.parseLong(longStr);
        } catch (NumberFormatException ex) {

        }
        return defaultValue;

    }


    public static File getFileSystemProperty(String name) {
        return getFileSystemProperty(name, null);
    }

    public static File getFileSystemProperty(String name, File defaultFile) {
        String fileStr = System.getProperty(name);
        if (fileStr != null)
            return new File(fileStr);
        else
            return defaultFile;
    }
}
