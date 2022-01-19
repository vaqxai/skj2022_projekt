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
	public boolean silentMode = false;

	// for client communication
	public TCPServer nodeTcpServer = null;

	// for inter-node communication
	public NodeUDPServer nodeUdpServer = null;

	private ArrayList<String> innerAddresses = new ArrayList<>();
	private ArrayList<String> outerAddresses = new ArrayList<>();

	private void printInfo(String info){
		if(!silentMode)
			System.out.println("[Node " + identifier + "]: " + info);
	}

	private void processAddCmd(String addNodeAddrStr){
		
		if(innerAddresses.size() < 4){
			innerAddresses.add(addNodeAddrStr);
			printInfo(" Added " + addNodeAddrStr + " to inner addresses.");
		} else if(outerAddresses.size() < 4){
			outerAddresses.add(addNodeAddrStr);
			printInfo(" Added " + addNodeAddrStr + " to outer addresses.");
		} else {
			throw new RuntimeException("Got add command despite having 8 networks conntected");
		}

	}

	private void processNewNode(ArrayList<String> addressTable, String newNodeAddrStr){

		// send new node info to existing nodes in own address tab
		for(String gatewayAddrStr : addressTable){
			String gatewayAddr = gatewayAddrStr.split(":")[0];
			int gatewayPort = Integer.parseInt(gatewayAddrStr.split(":")[1]);
			nodeUdpServer.send("ADD " + newNodeAddrStr, gatewayAddr, gatewayPort);
			printInfo("Sent ADD to " + gatewayAddrStr + " with address " + newNodeAddrStr);
		}

		// send other nodes' info to the new node
		for(String gatewayAddrStr : addressTable){
			String newNodeAddr = newNodeAddrStr.split(":")[0];
			int newNodePort = Integer.parseInt(newNodeAddrStr.split(":")[1]);
			nodeUdpServer.send("ADD " + gatewayAddrStr, newNodeAddr, newNodePort);
			printInfo("Sent ADD to " + gatewayAddrStr + " with address " + newNodeAddrStr);
		}

		// add new node to own address table
		addressTable.add(newNodeAddrStr);
		printInfo(" Processed new node " + newNodeAddrStr + " and added them to the network and own table.");

	}

	private void nodeConnected(String gatewayAddrStr){

		if(innerAddresses.size() < 4){ // Inner addresses not full

			processNewNode(innerAddresses, gatewayAddrStr);

		} else if(outerAddresses.size() < 4){ // Outer addresses not full

			processNewNode(outerAddresses, gatewayAddrStr);

		} else {

			// TODO: Redirect to subnet member

		}

	}

	public NetworkNode(String identifier, int tcpPort, String gatewayAddr, int gatewayPort, String resources){

		this.identifier = identifier;
		printInfo("Initializing...");

		ExecutorService execSvc = Executors.newFixedThreadPool(2);

		String[] messageText = new String[3];
		boolean[] messageFromNode = new boolean[1];

		final ReentrantLock lock = new ReentrantLock();
		final Condition messageReceived = lock.newCondition();
		final Condition messageProcessed = lock.newCondition();

		nodeUdpServer = new NodeUDPServer(tcpPort, lock, messageReceived, messageProcessed, messageText, messageFromNode);
		nodeTcpServer = new TCPServer(tcpPort);

		execSvc.submit(nodeUdpServer);
		execSvc.submit(nodeTcpServer);

		nodeTcpServer.setSilentMode(true);
		nodeUdpServer.setSilentMode(true);

		// Client message callback
		
		nodeTcpServer.setAutoResponse((sock, message) -> {
			try{
				lock.lock();
				messageText[0] = message;
				messageText[1] = "" + sock.getInetAddress();
				messageText[2] = "" + sock.getPort();
				messageFromNode[0] = false;
				messageReceived.signalAll();
				messageProcessed.await();
				lock.unlock();
			} catch (InterruptedException e){
				System.err.println(e);
			}
			return "";
		});

		// Network handshake for non-first nodes

		if((gatewayAddr != null) && (gatewayPort > 0)){

			innerAddresses.add(gatewayAddr + ":" + gatewayPort);
			nodeUdpServer.send("CON", gatewayAddr, gatewayPort);
			printInfo("Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

		}

		// Main loop

		printInfo("Started!");

		while(true){
			lock.lock();
			try{

				messageReceived.await();

				printInfo("INCOMING MSG: [" + messageText[1] + ":" + messageText[2] + "] > " + messageText[0]);

				String[] messageTextArray = messageText[0].split(" ");

				if(messageFromNode[0]){

					switch(messageTextArray[0]){
						case "CON":
							nodeConnected(messageText[1] + ":" + messageText[2]);
							break;
						case "ADD":
							processAddCmd(messageTextArray[1]);
							break;
						default:
							// TODO: Unknown command
							break;
					}

				}

				if(messageText[0].equals("TERMINATE")) break;

				messageText[0] = "ERROR";

				System.out.println(identifier + " Waiting...");

			} catch (InterruptedException e) {
				System.err.println(e);
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
				default:
					if(resources == null) resources = args[i];
					else resources += " " + args[i];
			}
	}

	new NetworkNode(identifier, tcpPort, gatewayAddr, gatewayPort, resources);

	}
}
