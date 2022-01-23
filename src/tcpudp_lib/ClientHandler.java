package tcpudp_lib;
import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.function.*;

/**
	* This class handles a single client that's connected to the server.
	* Copied from https://github.com/vaqxai/java-tcpclientserver/
 */
public class ClientHandler implements Runnable {
	private final Socket clientSocket;
	private boolean silent = false;
	private BufferedReader input = null;
	private PrintWriter output = null;
	private LinkedList<Message> received = new LinkedList<>();

	/**
	 * Automatic response to this client: if you return "", it will not send a response.
	 */
	private BiFunction<Socket, String, String> onReceiveMessage = null;

	/**
	 * Creates a ClientHandler with the provided server socket, and a response callback.
	 * @param socket the connected client's socket instance
	 * @param silent should it be silent
	 */
	public ClientHandler(Socket socket, boolean silent){
		this.clientSocket = socket;
		this.silent = silent;
	}

	/**
	 * Changes this server's client's automatic response callback
	 * @param newCallback the new callback, return "" to stop the server from sending an automatic response.
	 */
	public void setAutoResponse(BiFunction<Socket, String, String> newCallback){
		this.onReceiveMessage = newCallback;
	}

	/**
	 * 
	 * @return the current callback function
	 */
	public BiFunction<Socket, String, String> getAutoResponse(){
		return this.onReceiveMessage;
	}

	/**
	 * 
	 * @return this client's socket for direct interface
	 */
	public Socket getSocket(){
		return this.clientSocket;
	}

	/**
	 * 
	 * @param message send data directly to this client
	 */
	public void send(Object message){
		output.println(String.valueOf(message));
	}

	/**
	 * 
	 * @return get latest message the client has sent to us (will hang its thread if there's no incoming messages)
	 */
	public String get(){
		try{
		return input.readLine();
		} catch(IOException e){
			System.out.println(e);
			return "ERROR";
		}
	}

	public void run(){
		
		try {

			output = new PrintWriter(clientSocket.getOutputStream(), true);
			input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

			String line;

			while ((line = input.readLine()) != null) {

				if(onReceiveMessage != null){
					String out = onReceiveMessage.apply(clientSocket, line);
					if(out != "")
						output.println(out);
				}

				received.add(new Message(line, clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort()));
				if (!this.silent)
					System.out.println("TCP SERVER ON " + this.getSocket().getLocalPort() + " RECEIVED A MESSAGE: " + line);
			}


		} catch (IOException e) {
			System.out.println(e);
		}

	}

}
