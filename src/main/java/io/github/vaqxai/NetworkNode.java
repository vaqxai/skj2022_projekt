package io.github.vaqxai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NetworkNode {

	public final String identifier;

	// for client communication
	public TCPServer nodeTcpServer = null;

	// for inter-node communication
	public NodeUDPServer nodeUdpServer = null;

	private ArrayList<String> innerAddresses = new ArrayList<>();
	private ArrayList<String> outerAddresses = new ArrayList<>();

	private void nodeConnected(String gatewayAddrStr){

		if(innerAddresses.size() < 4){ // Inner addresses not full

			// TODO: Add to own network

		} else if(outerAddresses.size() < 4){ // Outer addresses not full

			// TODO: Add to subnet

		} else {

			// TODO: Redirect to subnet member

		}

	}

	public NetworkNode(String identifier, int tcpPort, String gatewayAddr, int gatewayPort, String resources){

		this.identifier = identifier;

		ExecutorService execSvc = Executors.newFixedThreadPool(2);

		String[] messageText = new String[3];

		final ReentrantLock lock = new ReentrantLock();
		final Condition messageReceived = lock.newCondition();

		nodeUdpServer = new NodeUDPServer(tcpPort, lock, messageReceived, messageText);
		nodeTcpServer = new TCPServer(tcpPort);

		execSvc.submit(nodeUdpServer);
		execSvc.submit(nodeTcpServer);

		nodeTcpServer.setSilentMode(true);
		nodeUdpServer.setSilentMode(true);
		nodeTcpServer.setAutoResponse((sock, message) -> {
			lock.lock();
			messageText[0] = message;
			messageText[1] = "" + sock.getInetAddress();
			messageText[2] = "" + sock.getPort();
			messageReceived.signalAll();
			lock.unlock();
			return "";
		});

		if((gatewayAddr != null) && (gatewayPort > 0)){

			innerAddresses.add(gatewayAddr + ":" + gatewayPort);
			nodeUdpServer.send("HELLO", gatewayAddr, gatewayPort);

		}

		while(true){
			lock.lock();
			try{
				messageReceived.await();
				System.out.println("Network mode received message:");
				System.out.println(messageText[1] + ":" + messageText[2] + " > " + messageText[0]);
				if(messageText[0].equals("TERMINATE")) break;
				messageText[0] = "ERROR";
			} catch (InterruptedException e) {
				// TODO: Handle interruption
				break;
			}
			lock.unlock();
		}

		System.out.println("END");

	}

	public static void main(String[] args) throws IOException {

	// parameter storage
	String gatewayAddr = null;
	int gatewayPort = 0;
	String identifier = null;
	String resources = null;
	int tcpPort = 0;

	// Parameter scan loop
	for(int i=0; i<args.length; i++) {
			switch (args[i]) {
				case "-ident":
					identifier = args[++i];
					break;
				case "-gateway":
					String[] gatewayArray = args[++i].split(":");
					gatewayAddr = gatewayArray[0];
					gatewayPort = Integer.parseInt(gatewayArray[1]);
					break;
				case "-tcpport":
					tcpPort = Integer.parseInt(args[++i]);
				break;
				case "terminate":
					resources = "TERMINATE";
					break;
				default:
					if(resources == null) resources = args[i];
					else if(! "TERMINATE".equals(resources)) resources += " " + args[i];
			}
	}

	if(resources.equals("TERMINATE")){
		// TODO: Handle terminate
	} else {
		new NetworkNode(identifier, tcpPort, gatewayAddr, gatewayPort, resources);
	}

	}
}
