package org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.apache.commons.io.IOUtils;

/**
 * Wrapper to provide a <code>PrintStream</code> for writing content to
 * and a corresponding <code>getContent()</code> method to get the content
 * which has been written to the PrintStream.
 * 
 * The reason is from the async Pipeline <code>Handle</code> we don't have
 * an active <code>TaskListener.getLogger()</code> anymore this means everything
 * written to the PrintStream (logger) will not be printed to the Pipeline log.
 * Therefore we provide this PrintStream for logging and the content can be
 * obtained later via <code>getContent()</code>.
 */
public class PrintStreamWrapper
{

    private final ByteArrayOutputStream byteStream;
    private final PrintStream printStream;

    public PrintStreamWrapper() throws UnsupportedEncodingException {
        byteStream = new ByteArrayOutputStream();
        printStream = new PrintStream(byteStream, false, "UTF-8");
    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    /**
     * Returns all logs since creation and closes the streams.
     *
     * @return all logs.
     * @throws IOException
     *            if UTF-8 charset is not supported.
     */
    public String getContent() throws IOException {
        String string = byteStream.toString("UTF-8");
        close();
        return string; 
    }

    /**
     * Closes the streams.
     */
    public void close() {
        IOUtils.closeQuietly(printStream);
        IOUtils.closeQuietly(byteStream);
    }

}
