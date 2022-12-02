import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Request Schema 1024 in total
 * For index:
 * Only 1 byte of 1
 * For get file:
 * First byte of 2, followed by the 2 byte integer of sequence number, followed by file name
 **/
public class UDPclient {
    private static InetAddress host;
    private static int port = 1025;

    private static void indexRequest(DatagramSocket socket){
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte) 0);
        data[0] = 1;
        DatagramPacket request = new DatagramPacket(data, 1, host, port);
        try {
            socket.send(request);
        } catch (IOException e) {
            System.out.print(e);
        }
    }
    private static void indexResponse(DatagramSocket socket){
        try {
            byte[] dataForResponse = new byte[1024];
            DatagramPacket response = new DatagramPacket(dataForResponse, dataForResponse.length);
            socket.receive(response);
            System.out.print(new String(response.getData(), 0, response.getLength(),"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.out.print(e);
        }
    }

    private static void fileRequest(DatagramSocket socket, String filename) {
        byte[] fileNameBytes = filename.getBytes(Charset.forName("UTF-8"));
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte) 0);
        data[0] = 2;
        int sequence = 0;
        int status = 0;
        do {
            data[1] = (byte) ((sequence >> 8) & 0xff);
            data[2] = (byte) (sequence & 0xff);
            System.out.println("Ask for sequence " + (int)data[1] + (int)data[2]);
            int len = 0;
            for (int i = 3; i < 1024 && i - 3 < fileNameBytes.length; i++) {
                data[i] = fileNameBytes[i - 3];
                len++;
            }
            DatagramPacket toSend = new DatagramPacket(data, len+3, host, port);
            try {
                socket.send(toSend);
            } catch (IOException e) {
                System.out.print(e);
                break;
            }
            status = fileResponse(socket, filename, sequence);

            if(status == 3) {
                // try three more times, if all failed, then give up
                for (int i = 0; i < 3; i++){
                    try {
                        socket.send(toSend);
                    } catch (IOException e) {
                        System.out.print(e);
                    }
                    status = fileResponse(socket, filename, sequence);
                    if (status != 3) break;
                }
                if (status == 3) {
                    System.out.println("Connection error. Gave up.");
                    return;
                }
            }

            if (status == 1) {
                System.out.println("File received");
                return;
            } else if (status == 2) {
                return;
            }

            sequence++; // TODO: dealing with end of file
        } while (status != 1);
    }

    /**
     * @param socket
     * @param filename
     * @return the status number: 0 for data, 1 for end, 2 for file not found, 3 for exception to try again
     */
    private static int fileResponse(DatagramSocket socket, String filename, int sequenceSupposed){
        byte[] dataForResponse = new byte[1024];
        DatagramPacket response = new DatagramPacket(dataForResponse, dataForResponse.length);
        try {
            // TODO: dealing with writing to file
            socket.receive(response);

            // parsing the datagram
            byte[] received = response.getData();
            int sequenceReceived = ((received[0] & 0xff ) << 8 ) + ((received[1] & 0xff));
            int status = received[2];

            if (status == 0 || status == 1){
                if (sequenceReceived == sequenceSupposed){
                    Path path = Paths.get("received", filename);
                    byte[] toWrite = Arrays.copyOfRange(response.getData(),3, response.getLength());


                    if (sequenceReceived == 0) {
                        // create file if not exist
                        Files.write(path, toWrite);
                    }else{
                        // Append to file
                        Files.write(path, toWrite, StandardOpenOption.APPEND);
                    }

                    return status;
                }else{
                    System.out.println("Received wrong sequence. Should be " + sequenceSupposed + "but "+ sequenceReceived+ " . Will request for the correct one");
                    return 3;
                }
            } else if (status == 2) {
                System.out.println("File not found");
                return 2;
            }

        } catch (IOException e) {
            System.out.println("Socket IO exception. Will try to request again.");
            return 3;
        }
        return 3;
    }
    public static void main(String[] args) {
        try {
            host = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        try (DatagramSocket socket = new DatagramSocket(0);) {
            socket.setSoTimeout(10000);
            BufferedReader cliReader = new BufferedReader(new InputStreamReader(System.in)); // Sys in: cliReader

            while (true) {
                String command = cliReader.readLine();

                if (command.equals("index")) {
                    indexRequest(socket);
                    System.out.println("send command: " + command);
                    indexResponse(socket);
                } else if (command.equals("q")) {
                    break;
                } else {
                    String[] commands = command.split(" ");
                    if ((commands.length <= 1) || !commands[0].equals("get")) {
                        System.out.println("Wrong syntax");
                    }else{
                        fileRequest(socket, commands[1]);
                    }
                }
            }
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.out.println("could not connect to server");
        }
    }
}