package io.github.vaqxai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
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

	HashMap<String, Integer> waitingDenials = new HashMap<>();

	/**
	 * Where String is order ID : clientIdentifier:sequentialNumber
	 */
	HashMap<String, ResourceRequest> requestsInProgress = new HashMap<>();

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

	private String getOwnAddressString(){
		try{
			return Inet4Address.getLocalHost().getHostAddress() + ":" + nodeUdpServer.getSocket().getLocalPort();
		} catch (UnknownHostException e) {
			return Inet4Address.getLoopbackAddress().getHostAddress() + ":" + nodeUdpServer.getSocket().getLocalPort();
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

			resources.put(resName, new NetworkResource(resName, getOwnAddressString(), resAmount));
		}

	}

	private void processLock(String commandArgs){

		String[] commandArray = commandArgs.split(" ");
		String originatorAddress = commandArray[0];
		String orderID = commandArray[1];
		String[] orderContents = Arrays.copyOfRange(commandArray, 2, commandArray.length);
		HashMap<String, Integer> orderParts = new HashMap<>();
		int totalResourcesNeeded = 0;

		for(String orderPart : orderContents){
			orderParts.put(orderPart.split(":")[0], Integer.parseInt(orderPart.split(":")[1]));
			totalResourcesNeeded += Integer.parseInt(orderPart.split(":")[1]);
		}

		HashMap<String, Integer> originalParts = new HashMap<>(orderParts);

		// how many resources can we lock?
		int lockedAmt = 0;
		for(Entry<String, NetworkResource> res : resources.entrySet()){
			if(orderParts.containsKey(res.getKey())){
				int tempLockedAmt = res.getValue().lock(orderParts.get(res.getKey()));
				if(tempLockedAmt > 0)
					orderParts.replace(res.getKey(), orderParts.get(res.getKey()) - tempLockedAmt);
				lockedAmt += tempLockedAmt;
			}
		}

		// partial request filled
		if(lockedAmt < totalResourcesNeeded && outerAddresses.size() > 0){

			waitingDenials.put(orderID + "/" + originatorAddress, outerAddresses.size());

			String remainderReceiver = outerAddresses.get((int)Math.round(Math.random() * (outerAddresses.size() - 1)));

			for(Entry<String, Integer> res : orderParts.entrySet()){

				int amountRemaining = res.getValue() % outerAddresses.size();
				int requestAmount = (res.getValue() - amountRemaining ) / outerAddresses.size();
				
				for(String address : outerAddresses){
					int amountToAsk = requestAmount;
					if(address.equals(remainderReceiver)){
						amountToAsk += amountRemaining;
					}
					nodeUdpServer.send("LOK " + originatorAddress + " " + orderID + " " + res.getKey() + ":" + amountToAsk, address.split(":")[0], Integer.parseInt(address.split(":")[1]));

					printInfo("Forwarded LOK to " + address + " for " + res.getKey() + ":" + amountToAsk);
				}

			}

		} else if (lockedAmt == totalResourcesNeeded) { // full request filled

			// LKR to originator
			String lkrMessage = "SUC " + originatorAddress + " " + orderID + " ";
			for(Entry<String, Integer> orderPart : orderParts.entrySet()){
				lkrMessage += orderPart.getKey() + ":" + (originalParts.get(orderPart.getKey()) - orderPart.getValue());
			}
			
			// Send full order fill info to originator
			nodeUdpServer.send(lkrMessage, originatorAddress.split(":")[0], Integer.parseInt(originatorAddress.split(":")[1]));

			printInfo("Sent SUC to " + originatorAddress + " with the following contents: " + lkrMessage);

		} else { // nothing filled, and can't forward. Send failure straight away.

			nodeUdpServer.send("FIL " + orderID, originatorAddress.split(":")[0], Integer.parseInt(originatorAddress.split(":")[1]));

			printInfo("Sent FIL to " + originatorAddress + " for order " + orderID + " because a request could not be filled or forwarded");

		}

		// 1. Check our own resources for availability

		// 2. If we can't fully fill the request, but can fill it partially, lock our resource, send a response, and forward a partial request to our sub-net if we have it.

		// 3. If we can't fill the request at all, just forward a partial request to our sub-net if we have one.

		// Send a negative response if we can't fill the request at all and we don't have a subnet.

	}

	private void processRedirect(String commandArgs){

			innerAddresses.remove(0);

			String gatewayAddr = commandArgs.split(":")[0];
			int gatewayPort = Integer.parseInt(commandArgs.split(":")[1]);

			innerAddresses.add(commandArgs);
			nodeUdpServer.send("CON", gatewayAddr, gatewayPort);
			printInfo("  Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

	}

	private void processLockResponse(String commandArgs, boolean searchEnded){

		String[] commandArray = commandArgs.split(" ");
		ResourceRequest request = requestsInProgress.get(commandArray[2].split("_")[0]);


		if (searchEnded) {
			String requestID = commandArray[2].split("_")[0];
			waitingDenials.replace(requestID, waitingDenials.get(requestID) - 1);

			// this was the last response, and the request is fully fulfilled
			if(waitingDenials.get(requestID) == 0 && commandArray[1].split("_")[2].equals("1") && request.isFullyLocked()){
				request.finalizeOrder(nodeUdpServer); // try to reserve resources on nodes with held locks

			// this was the last response, first search of outer addresses (if they were present), and the request was not fulfilled
			} else if(waitingDenials.get(requestID) == 0 && commandArray[1].split("_")[2].equals("1") && outerAddresses.size() > 0) {
				ArrayList<String> destinations = new ArrayList<>();
				for(String address : outerAddresses){
					if(!request.getFailedNodes().contains(address)){
						destinations.add(address);
					}
				}
				processLockSend(request, destinations);
			// if this was the last response, second search of outer addresses, and the request is not fully locked
			} else if(waitingDenials.get(requestID) == 0 && commandArray[1].split("_")[2].equals("2") && outerAddresses.contains(commandArray[0])){
				
			}
		}

	}

	private void processFailResponse(String commandArgs){
		// TODO: Process fail
	}

	private void processUnlock(String commandArgs){
		// TODO: Process unlock
	}

	private void processReservation(String argsStr){

		String[] args = argsStr.split(" ");

		for(int i = 2; i < args.length; i++){

			String resourceIdentifier = args[i].split(":")[0];
			int resourceAmount = Integer.parseInt(args[i].split(":")[1]);

			resources.get(resourceIdentifier).reserve(args[1], resourceAmount);
		}
		
	}

	private void processRequestFail(ResourceRequest request){
		request.getClientHandler().send("FAILED");
		request.release(nodeUdpServer);
		request.status = RequestStatus.DENIED;
	}

	public void processLockSend(ResourceRequest request, ArrayList<String> addresses){

		HashMap<String, Integer> requestedResources = request.getOrderRemaining();

		for (Entry<String, Integer> orderPart : requestedResources.entrySet()){

			int amountRemaining = requestedResources.get(orderPart.getKey());

			int requestAmount = 0;
			String remainderReceiver = "";

			if (addresses.size() > 0){

				requestAmount = (amountRemaining - (amountRemaining % addresses.size()) ) / addresses.size();
				amountRemaining = amountRemaining % addresses.size();

				remainderReceiver = addresses.get((int)Math.round(Math.random() * (addresses.size() - 1)));

			} else {

				// Are you trying to send a lock to zero addresses?

			}

			for(String address : addresses){

				request.status = RequestStatus.WAITING;
				
				int amountToRequest = requestAmount;
				if(address.equals(remainderReceiver)) amountToRequest += amountRemaining;

				waitingDenials.put(request.originator + request.orderId + "/" + getOwnAddressString(), addresses.size());

				nodeUdpServer.send(
					"LOK " + getOwnAddressString() + " " + request.originator + ":" + request.orderId + "_" + 1 + " " + orderPart.getKey() + ":" + amountToRequest,
					address.split(":")[0],
					Integer.parseInt(address.split(":")[1])
				);

				printInfo("Failed to allocate needed resources on the local node. Forwarding the request to the subnet.");

			}
			
		}

	}

	public void printResources(){

		printInfo("Resources:");
		for(Entry<String, NetworkResource> res : resources.entrySet()){
			NetworkResource resVal = res.getValue();
			printInfo("  Name: " + resVal.getIdentifier());
			printInfo("    Available: " + resVal.getAvailable());
			printInfo("    Locked: " + resVal.getLocked());
			printInfo("    Allocated: " + resVal.getOwners().toString());
		}

	}

	public NetworkNode(String identifier, int tcpPort, String gatewayAddr, int gatewayPort, String resources){

		this.identifier = identifier + " (:" + tcpPort + ")";
	
		printInfo("Initializing...");

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

		processResources(resources);

		// Network handshake for non-first nodes

		if((gatewayAddr != null) && (gatewayPort > 0)){

			innerAddresses.add(gatewayAddr + ":" + gatewayPort);
			nodeUdpServer.send("HONK", gatewayAddr, gatewayPort);
			printInfo("  Asked (honked at) " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

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
					break;
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
				if(messageText[0].length() > 3){
					command = messageText[0].substring(0, 3);
					commandArgs = messageText[0].substring(4);
				} else if (messageText[0].length() == 3) {
					command = messageText[0];
				}

				// Inter-node communications

				if(messageFromNode[0]){

					switch(command){
						case "HON"://K // New node connects to the network (honks to us) via us
							nodeConnected(messageText[1] + ":" + messageText[2]);
							break;
						case "ADD": // We are adding a new node to our tables
							processAddCmd(commandArgs);
							break;
						case "DIR": // We are being redirected
							processRedirect(commandArgs);
							break;
						case "LOK": // Someone requests our resources
							processLock(commandArgs);
							break;
						case "LKR": // Someone responded to one of our locks
							processLockResponse(messageText[1] + ":" + messageText[2] + " " + commandArgs, false);
							break;
						case "SUC": // Someone responded to one of our locks, and has ended their search
							processLockResponse(messageText[1] + ":" + messageText[2] + " " + commandArgs, true);
						case "FIL": // Someone ended their search and failed their locks
							processFailResponse(messageText[1] + ":" + messageText[2] + " " + commandArgs);
						case "ULK": // Someone wants to unlock our resources
							processUnlock(commandArgs);
						case "RES": // Someone wants to reserve our resources
							processReservation(messageText[1] + ":" + messageText[2] + " " + commandArgs);
							break;
					}

				}

				// Client-node communication, only communication to be received here, is a reservation request.

				else if (messageText[0].split(" ").length > 1) {

					String[] clientMessageArray = messageText[0].split(" ");

					ClientHandler cli = null;

					for(ClientHandler client : nodeTcpServer.getAllClients()){
						Socket sock = client.getSocket();
						if(String.valueOf(sock.getInetAddress()).equals(messageText[1]) &&
						String.valueOf(sock.getPort()).equals(messageText[2])){
							cli = client;
							break;
						}
					}

					String clientIdentifier = clientMessageArray[0];
					String[] requestArray = Arrays.copyOfRange(clientMessageArray, 1, clientMessageArray.length);
					ResourceRequest request = new ResourceRequest(clientIdentifier, cli, requestArray);
					request.status = RequestStatus.PROCESSING;

					// 1. Check if we have the resources they want ourselves, and respond immediately if we do, block all we can if we don't.

					int[] amountLocked = {0};

					this.resources.values().stream().filter(res -> {
						return request.getOrder().keySet().contains(res.getIdentifier());
					}).forEach(res -> {
						int tempLockedAmt = res.lock(request.getOrder().get(res.getIdentifier()));
						amountLocked[0] += tempLockedAmt;
						request.addLockedResource(getOwnAddressString(), res.getIdentifier(), tempLockedAmt);
					});

					if(amountLocked[0] == request.order.values().stream().reduce(0, (a, b) -> a + b)){

						request.status = RequestStatus.FINALIZED;
						request.finalizeOrder(nodeUdpServer); // this will just send us reservations

					} else {
						if(outerAddresses.size() == 0){
							processLockSend(request, innerAddresses);
						} else {
							processLockSend(request, outerAddresses);
						}
					}



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
