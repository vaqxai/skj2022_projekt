package io.github.vaqxai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NetworkNode {

	public final String identifier;
	public boolean silentMode = false;
	private int lastRedirIndex = -1;

	// for client communication
	public TCPServer nodeTcpServer = null;

	// for inter-node communication
	public NodeUDPServer nodeUdpServer = null;

	ArrayList<String> innerAddresses = new ArrayList<>();
	ArrayList<String> outerAddresses = new ArrayList<>();

	HashMap<String, NetworkResource> resources = new HashMap<>();

	private void printInfo(String info){

		if(!silentMode)
			System.out.println("[" + identifier + "] " + info);
	}

	private void processAddCmd(String addNodeAddrStr){

		String[] addNodeAddrStrArr = addNodeAddrStr.split(" ");
		
		for (String addNodeAddr : addNodeAddrStrArr){
			if(innerAddresses.size() < 4){
				innerAddresses.add(addNodeAddr);
				printInfo("  Added " + addNodeAddr + " to inner addresses.");
			} else if(outerAddresses.size() < 4){
				outerAddresses.add(addNodeAddr);
				printInfo("  Added " + addNodeAddr + " to outer addresses.");
			} else {
				throw new RuntimeException("Got add command despite having 8 networks conntected");
			}
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
		String addStr = "";
		for(String gatewayAddrStr : addressTable){
			addStr += gatewayAddrStr + " ";
		}

		if(addStr != ""){
			String newNodeAddr = newNodeAddrStr.split(":")[0];
			int newNodePort = Integer.parseInt(newNodeAddrStr.split(":")[1]);
			nodeUdpServer.send("ADD " + addStr, newNodeAddr, newNodePort);
			printInfo("Sent ADD to " + newNodeAddrStr + " with addresses " + addStr);
		}

		// add new node to own address table
		addressTable.add(newNodeAddrStr);
		printInfo("  Processed new node " + newNodeAddrStr + " and added them to the network and own table.");

	}

	private void nodeConnected(String gatewayAddrStr){

		if(innerAddresses.size() < 4){ // Inner addresses not full

			processNewNode(innerAddresses, gatewayAddrStr);

		} else if(outerAddresses.size() < 4){ // Outer addresses not full

			processNewNode(outerAddresses, gatewayAddrStr);

		} else {
			
			if(lastRedirIndex > 2)
				lastRedirIndex = -1;
			
			nodeUdpServer.send(
				"DIR " + outerAddresses.get(++lastRedirIndex),
				gatewayAddrStr.split(":")[0],
				Integer.parseInt(gatewayAddrStr.split(":")[1])
			);

			printInfo("Redirected " + gatewayAddrStr + " to " + outerAddresses.get(lastRedirIndex) + " because the current network member limit has been reached.");

		}

	}

	private void processResources(String resourceString){

		String[] resourceArray = resourceString.split(" ");

		for(String singleResString : resourceArray){
			String resName = singleResString.split(":")[0];
			int resAmount = Integer.parseInt(singleResString.split(":")[1]);

			resources.put(resName, new NetworkResource(resName, resAmount));
		}

	}

	private void processRedirect(String commandArgs){

			innerAddresses.remove(0);

			String gatewayAddr = commandArgs.split(":")[0];
			int gatewayPort = Integer.parseInt(commandArgs.split(":")[1]);

			innerAddresses.add(commandArgs);
			nodeUdpServer.send("CON", gatewayAddr, gatewayPort);
			printInfo("  Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

	}

	public void printResources(){

		printInfo("Resources:");
		for(Entry<String, NetworkResource> res : resources.entrySet()){
			NetworkResource resVal = res.getValue();
			printInfo("  Name: " + resVal.getIdentifier());
			printInfo("    Available: " + resVal.getAvailable());
			printInfo("    Locked: " + resVal.getLocked());
		}

	}

	public NetworkNode(String identifier, int tcpPort, String gatewayAddr, int gatewayPort, String resources){

		String ident = "?";
		try {
			ident = identifier + " (" + String.valueOf(Inet4Address.getLocalHost()).split("/")[1] + ":" + tcpPort + ")";
		} catch (UnknownHostException e) {
			ident = identifier;
		} finally {
			this.identifier = ident;
		}

		printInfo("Initializing...");

		processResources(resources);

		ExecutorService execSvc = Executors.newFixedThreadPool(2);

		String[] messageText = new String[3];
		boolean[] messageFromNode = new boolean[1];

		final ReentrantLock lock = new ReentrantLock();
		final Condition messageReceived = lock.newCondition();
		final Condition messageProcessed = lock.newCondition();

		nodeUdpServer = new NodeUDPServer(tcpPort);
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

		// Node message callback

		nodeUdpServer.setCallback((packet) -> {

			try{
			lock.lock();
			InetAddress incomingAdddress = packet.getAddress();

			String receivedStr = new String(packet.getData(), 0, packet.getLength());

			messageText[0] = receivedStr;
			messageText[1] = incomingAdddress.getHostAddress();
			messageText[2] = "" + packet.getPort();
			messageFromNode[0] = true;

			messageReceived.signalAll();
			//messageProcessed.await();
			lock.unlock();

			} catch (Exception e){
				System.err.println(e);
				lock.unlock();
			}

		});

		// Network handshake for non-first nodes

		if((gatewayAddr != null) && (gatewayPort > 0)){

			innerAddresses.add(gatewayAddr + ":" + gatewayPort);
			nodeUdpServer.send("CON", gatewayAddr, gatewayPort);
			printInfo("  Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

		}

		// Main loop

		printInfo("Started!");

		while(true){
			lock.lock();
			try{

				// Ignore empty/improperly structured messages

				if(messageText[0] == null || messageText[1] == null || messageText[2] == null){
					continue;
				}

				printInfo("INCOMING MSG: [" + messageText[1] + ":" + messageText[2] + "] > " + messageText[0]);

				// Control messages

				if(messageText[0].equals("TERMINATE")){
					// Propagate termination, like a virus.
					for(String addrStr : outerAddresses){
						nodeUdpServer.send("TERMINATE", addrStr.split(":")[0], Integer.parseInt(addrStr.split(":")[1]));
					}
					for(String addrStr : innerAddresses){
						nodeUdpServer.send("TERMINATE", addrStr.split(":")[0], Integer.parseInt(addrStr.split(":")[1]));
					}
				};

				// Debug/Test commands

				if(messageText[0].equals("RESOURCES")) printResources();

				if(messageText[0].startsWith("LOCK")){
					String res = messageText[0].split(" ")[1];
					this.resources.get(res.split(":")[0]).lock(Integer.parseInt(res.split(":")[1]));
				}

				// Regular messages/inter-node/client communications

				// Parse the message into a command and arguments, to be executed

				String command = "";
				String commandArgs = "";
				if(messageText[0].length() > 4){
					command = messageText[0].substring(0, 3);
					commandArgs = messageText[0].substring(4);
				} else if (messageText[0].length() == 3) {
					command = messageText[0];
				}

				// Inter-node communications

				if(messageFromNode[0]){

					switch(command){
						case "CON": // New node connects to the network via us
							nodeConnected(messageText[1] + ":" + messageText[2]);
							break;
						case "ADD": // We are adding a new node to our tables
							processAddCmd(commandArgs);
							break;
						case "DIR": // We are being redirected
							processRedirect(commandArgs);
							break;
						default: // We don't know
							printInfo("Unknown Command");
							break;
					}

				}

				// Client-node communication, only communication to be received here, is a reservation request.

				else {

					// TODO: Client-node communication

				}

				messageText[0] = "ERROR";

				messageProcessed.signalAll();

				messageReceived.await();

			} catch (InterruptedException e) {
				System.err.println(e);
				break;
			} finally {
				lock.unlock();
			}
		}

		printInfo("END");

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
