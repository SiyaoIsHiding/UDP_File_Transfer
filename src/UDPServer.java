import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Request Schema 1024 in total
 * For index:
 * Only 1 byte of 1
 * For get file:
 * First byte of 2, followed by the 2 byte integer of sequence number, followed by file name
 *
 * Response Schema 1024 in total
 * For index:
 * immediately the list
 * For get file:
 * First two bytes of integer sequence number, one byte of end of file ( 0 for data, 1 for end, 2 for file not found), and then the data of 1021
 */
public class UDPServer {
    private final int port;
    private final String folder;
    public UDPServer(int port, String folder) throws IOException {
        this.port = port;
        this.folder = folder;
    }
    public void start(){
        ExecutorService pool = Executors.newFixedThreadPool(100);

        try (DatagramSocket socket = new DatagramSocket(1025)){
            while (true){
                try{
                    DatagramPacket request = new DatagramPacket(new byte[1024],1024);
                    socket.receive(request);
                    pool.submit(new HandleRequest(socket, request, folder));
                } catch (IOException e) {
                    System.out.println(e);
                } catch (RuntimeException e){
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

    }

    private class HandleRequest implements Callable<Void> {
        private DatagramSocket socket;
        private DatagramPacket request;
        private String folder;

        private HandleRequest(DatagramSocket socket, DatagramPacket request, String folder){
            this.socket = socket;
            this.request = request;
            this.folder = folder;
        }
        // TODO: file not found
        @Override
        public Void call() throws Exception {
            byte[] requestData = request.getData();
            if (requestData[0] == 1){
                handleIndex();
                System.out.println("Received index request");
            } else if (requestData[0] == 2) {
                handleFile();
                System.out.println("Received file request");
            }
            return null;
        }

        private void handleIndex(){
            Path path = Paths.get(this.folder);
            try {
                Path[] availableFiles = Files.list(path).toArray(Path[]::new);
                StringBuilder toSend = new StringBuilder();
                for (int i = 0; i < availableFiles.length; i++) {
                    toSend.append(availableFiles[i].getFileName() + "\r\n");
                }
                System.out.println(toSend);
                byte[] data = toSend.toString().getBytes(Charset.forName("UTF-8"));
                DatagramPacket response = new DatagramPacket(data, data.length, request.getAddress(), request.getPort());
                socket.send(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void handleFile(){
            // parse the datagram
            byte[] requestData = request.getData();

            int sequence = ((requestData[1] & 0xff ) << 8 ) + ((requestData[2] & 0xff));
            String filename = new String(requestData, 3, request.getLength()-3, Charset.forName("UTF-8"));

            // preparing response
            byte[] toSend = new byte[1024];
            toSend[0] = requestData[1];
            toSend[1] = requestData[2];
            Path path = Paths.get(this.folder, filename);

            try {
                if (!Files.exists(path)){
                    // file not found
                    toSend[2] = 2;
                    DatagramPacket toSendPacket = new DatagramPacket(toSend, 3, request.getAddress(), request.getPort());
                    socket.send(toSendPacket);
                }else{
                    int lenPerPost = 1021;
                    byte[] data = Files.readAllBytes(path);
                    boolean end = (sequence+1)*lenPerPost >= data.length;
                    int len = end? (data.length-sequence*lenPerPost) : lenPerPost;
                    toSend[2] = (byte) (end? 1: 0);
                    data = Arrays.copyOfRange(data, sequence*lenPerPost, sequence*lenPerPost+lenPerPost);
                    for (int i = 3; i < 3+lenPerPost; i++){
                        toSend[i] = data[i-3];
                    }
                    DatagramPacket toSendPacket = new DatagramPacket(toSend, len+3, request.getAddress(), request.getPort());
                    socket.send(toSendPacket);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args){
        int port = 1025;
        String folder;
        if (args.length< 1){
            folder = "public";
        }else{
            folder = args[0];
        }
        try{
            UDPServer server = new UDPServer(port, folder);
            server.start();
        } catch (IOException e) {
            System.out.println("Wrong folder name");
        } catch (Exception e){
        }


    }
}
