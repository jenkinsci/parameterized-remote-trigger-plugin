package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import hudson.AbortException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.ConnectionResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class QueueItemTest {

    // QueueItem looks for "Location" so test specifically for that
    final static private String key = "Location";
    final static private String id = "4848912";
    final static private String location = String.format("http://example.com/jenkins/my-jenkins1/queue/item/%s/", id);

    // invalid header missing Location
    final static private Map<String, List<String>> noLocationHeader = new HashMap<String, List<String>>() {{
        put("Date", Collections.singletonList("Tue, 21 Apr 2020 02:26:47 GMT"));
        put("Server", Collections.singletonList("envoy"));
        put(null, Collections.singletonList("HTTP/1.1 201 Created"));
        put("Content-Length", Collections.singletonList("0"));
        put("X-Envoy-Upstream-Service-Time", Collections.singletonList("15"));
        put("X-Content-Type-Options", Collections.singletonList("nosniff"));
    }};

    // Add the Location to make valid header with typical capitalization
    final static private Map<String, List<String>> locationHeader = new HashMap<String, List<String>>(noLocationHeader) {{
        put(key, Collections.singletonList(location));
    }};

    // valid header with all lowercase.  Watch out for null key.
    final static private Map<String, List<String>> lowerCaseLocationHeader = locationHeader.entrySet().stream().collect(
                    Collectors.toMap(entry -> entry.getKey() == null ? null : entry.getKey().toLowerCase(),
                                     entry -> entry.getValue()));

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                        { noLocationHeader, false },
                        { locationHeader, true },
                        { lowerCaseLocationHeader, true }
        });
    }

    @Parameterized.Parameter()
    public Map<String, List<String>> header;

    @Parameterized.Parameter(1)
    public boolean isValid;

    @Test
    public void test() {
        // ConnectionResponse creates case-insensitive map of header
        ConnectionResponse connectionResponse = new ConnectionResponse(header, HttpURLConnection.HTTP_OK);

        try {
            QueueItem queueItem = new QueueItem(connectionResponse.getHeader());
            assertTrue("QueueItem should have thrown exception for invalid header: " + header, isValid);
            assertEquals(queueItem.getLocation(), location);
            assertEquals(queueItem.getId(), id);
        } catch (AbortException e) {
            assertFalse("QueueItem thew exception for valid header: " + header, isValid);
        }
    }
}